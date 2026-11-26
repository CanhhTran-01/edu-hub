package com.eduhub.edu_hub.aiassistant.application.service;

import com.eduhub.edu_hub.academic.api.dto.response.AdvisorAcademicProfileResponse;
import com.eduhub.edu_hub.aiassistant.api.dto.request.AdvisorChatRequest;
import com.eduhub.edu_hub.aiassistant.api.dto.response.AdvisorRequestStatusResponse;
import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.aiassistant.application.port.*;
import com.eduhub.edu_hub.aiassistant.application.port.AiAdvisorModelPort.*;
import com.eduhub.edu_hub.aiassistant.application.service.AdvisorChatExecution.AdvisorExecutionStoppedException;
import com.eduhub.edu_hub.aiassistant.domain.policy.AiContentPolicy;
import com.eduhub.edu_hub.aiassistant.infrastructure.adapter.AiTokenCounter;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.*;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/** Điều phối lượt SSE; không giữ transaction DB trong lúc model hoặc tool chạy. */
@Service
@Slf4j
public class AiAdvisorChatService {
    private static final String CAPABILITIES_MESSAGE =
            "Xin chào! Hiện tôi hỗ trợ tra cứu quy chế và xem tổng quan GPA, tín chỉ của bạn. "
                    + "Bạn muốn xem học vụ hiện tại hay tra cứu nội dung quy chế nào?";
    private final AiAccessService accessService;
    private final ChatHistoryService historyService;
    private final AiAdvisorModelPort modelPort;
    private final AiQuestionRateLimiter rateLimiter;
    private final AiAdvisorProperties properties;
    private final AiRagProperties ragProperties;
    private final AiTokenCounter tokenCounter;
    private final Clock clock;
    private final AsyncTaskExecutor executor;
    private final TaskScheduler scheduler;
    private final Map<UUID, AdvisorChatExecution> requestsById = new HashMap<>();
    private final Map<RequestIdentity, UUID> requestIdsByClientIdentity = new HashMap<>();
    private final Map<Long, AdvisorChatExecution> activeRequestsByUserId = new HashMap<>();

    public AiAdvisorChatService(
            AiAccessService accessService,
            ChatHistoryService historyService,
            AiAdvisorModelPort modelPort,
            AiQuestionRateLimiter rateLimiter,
            AiAdvisorProperties properties,
            AiRagProperties ragProperties,
            AiTokenCounter tokenCounter,
            Clock clock,
            @Qualifier("aiAdvisorExecutor") AsyncTaskExecutor executor,
            @Qualifier("aiAdvisorLifecycleScheduler") TaskScheduler scheduler) {
        this.accessService = accessService;
        this.historyService = historyService;
        this.modelPort = modelPort;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.ragProperties = ragProperties;
        this.tokenCounter = tokenCounter;
        this.clock = clock;
        this.executor = executor;
        this.scheduler = scheduler;
    }

    public AdvisorChatExecution prepareChat(Jwt principal, AdvisorChatRequest request) {
        Long userId = accessService.requireStudent(principal).getId();
        if (request == null
                || request.getClientRequestId() == null
                || request.getMessage() == null
                || request.getMessage().isBlank()
                || request.getMessage().length() > 2000
                || (request.getSessionId() != null && request.getSessionId().length() > 20))
            throw new AiAssistantException(ErrorCode.VALIDATION_ERROR);
        String question = AiContentPolicy.redactPromptText(AiContentPolicy.normalizeQuestion(request.getMessage()));
        // Ownership được kiểm tra trước khi mở SSE; model không có tham số User ID/Student ID.
        var conversation = historyService.loadConversation(userId, request.getSessionId());
        synchronized (this) {
            removeExpiredRequests();
            var identity = new RequestIdentity(userId, request.getClientRequestId());
            UUID previousRequestId = requestIdsByClientIdentity.get(identity);
            if (previousRequestId != null) throw conflictingRequest(previousRequestId);
            var active = activeRequestsByUserId.get(userId);
            if (active != null) throw conflictingRequest(active.getRequestId());
            if (activeRequestsByUserId.size() >= properties.getMaximumConcurrentRequests()
                    || requestsById.size() >= properties.getMaximumTrackedRequests())
                throw new AiAssistantException(ErrorCode.AI_ADVISOR_CAPACITY_EXCEEDED);
            rateLimiter.consumeQuestionAllowance(userId);
            var execution = new AdvisorChatExecution(
                    request.getClientRequestId(),
                    userId,
                    principal,
                    question,
                    conversation,
                    properties,
                    tokenCounter,
                    clock);
            requestsById.put(execution.getRequestId(), execution);
            requestIdsByClientIdentity.put(identity, execution.getRequestId());
            activeRequestsByUserId.put(userId, execution);
            return execution;
        }
    }

    public void startChat(AdvisorChatExecution execution) {
        if (!execution.tryScheduleWorker()) return;
        try {
            execution.publish("agent.started", Map.of("clientRequestId", execution.getClientRequestId()));
            execution.addScheduledTask(scheduler.schedule(() -> execution.limit("DEADLINE"), execution.getDeadline()));
            execution.addScheduledTask(scheduler.scheduleWithFixedDelay(execution::heartbeat, Duration.ofSeconds(10)));
            executor.execute(() -> executeChat(execution));
        } catch (RuntimeException exception) {
            execution.fail(ErrorCode.AI_SERVICE_UNAVAILABLE.getCode());
            releaseActiveRequest(execution);
        }
    }

    private void executeChat(AdvisorChatExecution execution) {
        try {
            // 1. Đăng ký worker thread và gọi AI Agent để xử lý câu hỏi.
            execution.registerWorkerThread();
            var modelAnswer = modelPort.answer(
                    execution, selectHistory(execution.getConversation().messages()));
            execution.checkCanContinue();

            // 2. Thu thập các tài liệu học vụ đã được cung cấp cho AI.
            var suppliedChunks = execution.getRetrievedChunks();
            var suppliedChunkIds = suppliedChunks.stream()
                    .map(KnowledgeChunkPort.RetrievedChunk::chunkId)
                    .collect(Collectors.toSet());

            // 3. Kiểm tra tính hợp lệ của câu trả lời và các nguồn trích dẫn.
            boolean hasValidKnowledgeAnswer = modelAnswer != null
                    && modelAnswer.content() != null
                    && modelAnswer.content().length() <= 20000
                    && AiContentPolicy.hasValidCitations(
                    modelAnswer.content(), modelAnswer.citationIds(), suppliedChunkIds);

            // 4. Lọc các tài liệu thực sự được AI trích dẫn hợp lệ.
            var citedChunkIds = hasValidKnowledgeAnswer ? new HashSet<>(modelAnswer.citationIds()) : Set.<Long>of();
            var citedChunks = suppliedChunks.stream()
                    .filter(chunk -> citedChunkIds.contains(chunk.chunkId()))
                    .toList();

            // 5. Xác định nội dung câu trả lời cuối cùng dựa trên dữ liệu đã xác minh.
            String content;
            if (execution.getAcademicProfile() != null) {
                // Chỉ template BE phát số liệu cá nhân; text model không được tự thêm GPA/tín chỉ.
                content = renderAcademicProfile(execution.getAcademicProfile());
                if (!citedChunks.isEmpty())
                    content += "\nTài liệu liên quan: "
                            + citedChunks.stream()
                            .map(chunk -> "[K" + chunk.chunkId() + "]")
                            .collect(Collectors.joining(", "))
                            + ".";

                // Sử dụng câu trả lời của AI nếu nội dung và trích dẫn hợp lệ.
            } else if (hasValidKnowledgeAnswer) {
                content = AiContentPolicy.redactPromptText(modelAnswer.content());

                // Xử lý trường hợp AI chưa thực thi bất kỳ Tool nào.
            } else if (execution.getStatus().toolCalls() == 0) {
                // Chỉ nhận câu hỏi làm rõ không chứa số khi chưa có bằng chứng; còn lại giới thiệu capability thật.
                content = modelAnswer != null
                        && modelAnswer.content() != null
                        && modelAnswer.content().length() <= 2000
                        && modelAnswer.content().strip().endsWith("?")
                        && modelAnswer.content().codePoints().noneMatch(Character::isDigit)
                        && modelAnswer.citationIds() != null
                        && modelAnswer.citationIds().isEmpty()
                        ? AiContentPolicy.redactPromptText(modelAnswer.content())
                        : CAPABILITIES_MESSAGE;

                // Sử dụng câu trả lời fallback khi không có đủ bằng chứng hợp lệ.
            } else {
                content = ragProperties.getFallbackMessage();
            }

            // 6. Hoàn tất execution và lưu câu trả lời đã xác minh vào lịch sử hội thoại.
            String verifiedContent = content;
            execution.complete(() -> historyService.recordExchange(
                    execution.getPrincipal(),
                    execution.getConversation(),
                    execution.getQuestion(),
                    verifiedContent,
                    citedChunks));

            // 7. Xử lý trường hợp execution bị hủy hoặc vượt giới hạn.
        } catch (AdvisorExecutionStoppedException exception) {
            // Hủy/limit đã phát terminal; không lưu text dở hoặc phát thêm lỗi.

            // 8. Xử lý lỗi phát sinh và đánh dấu execution thất bại.
        } catch (RuntimeException exception) {
            int code = exception instanceof BaseException businessException
                    ? businessException.getErrorCode().getCode()
                    : ErrorCode.AI_SERVICE_UNAVAILABLE.getCode();
            execution.fail(code);

            // 9. Giải phóng suất thực thi và ghi log tổng kết request.
        } finally {
            // Giữ suất đến khi worker thực sự dừng; cancel SDK có thể chưa giải phóng I/O ngay.
            releaseActiveRequest(execution);
            var status = execution.getStatus();
            log.info(
                    "Lượt Advisor kết thúc: requestId={}, actorId={}, state={}, modelCalls={}, toolCalls={}, reservedTokens={}",
                    status.requestId(),
                    execution.getUserId(),
                    status.state(),
                    status.modelCalls(),
                    status.toolCalls(),
                    status.reservedTokens());
        }
    }

    public AdvisorRequestStatusResponse findRequestStatus(Jwt principal, UUID requestId) {
        return requireOwnedExecution(principal, requestId).getStatus();
    }

    public AdvisorRequestStatusResponse cancelRequest(Jwt principal, UUID requestId) {
        var execution = requireOwnedExecution(principal, requestId);
        execution.cancel("USER_REQUEST");
        return execution.getStatus();
    }

    private AdvisorChatExecution requireOwnedExecution(Jwt principal, UUID requestId) {
        Long userId = accessService.requireStudent(principal).getId();
        synchronized (this) {
            removeExpiredRequests();
            var execution = requestsById.get(requestId);
            if (execution == null || !execution.getUserId().equals(userId))
                throw new AiAssistantException(ErrorCode.AI_CHAT_REQUEST_NOT_FOUND);
            return execution;
        }
    }

    private synchronized void releaseActiveRequest(AdvisorChatExecution execution) {
        activeRequestsByUserId.remove(execution.getUserId(), execution);
    }

    private void removeExpiredRequests() {
        // TODO(ADVISOR_REQUEST_PERSISTENCE): sau này lưu bền trạng thái/idempotency và artifact theo conversation.
        //  Runtime hiện giới hạn số bản ghi/thời gian; restart hoặc hết retention thì request không còn tra cứu được.
        var iterator = requestsById.values().iterator();
        while (iterator.hasNext()) {
            var execution = iterator.next();
            var status = execution.getStatus();
            if (!activeRequestsByUserId.containsValue(execution)
                    && status.finishedAt() != null
                    && !status.finishedAt()
                            .plus(properties.getRequestRetention())
                            .isAfter(LocalDateTime.now(clock))) {
                iterator.remove();
                requestIdsByClientIdentity.remove(
                        new RequestIdentity(execution.getUserId(), execution.getClientRequestId()));
            }
        }
    }

    private AiAssistantException conflictingRequest(UUID requestId) {
        return new AiAssistantException(ErrorCode.AI_CHAT_REQUEST_CONFLICT, "requestId", requestId.toString());
    }

    private List<ConversationMessage> selectHistory(List<ConversationMessage> history) {
        var selected = new ArrayList<ConversationMessage>();
        int estimatedTokens = 0;
        for (int index = history.size() - 1; index >= 0 && selected.size() < properties.getRecentMessages(); index--) {
            var message = history.get(index);
            String content = AiContentPolicy.redactPromptText(message.content());
            int messageTokens = tokenCounter.countTokens(content) + 16;
            if (estimatedTokens + messageTokens > ragProperties.getMemoryTokenLimit()) break;
            selected.add(new ConversationMessage(message.role(), content));
            estimatedTokens += messageTokens;
        }
        Collections.reverse(selected);
        return List.copyOf(selected);
    }

    private String renderAcademicProfile(AdvisorAcademicProfileResponse profile) {
        return "GPA tích lũy: " + displayNumber(profile.cumulativeGpa())
                + "\nGPA kỳ hiện tại: " + displayNumber(profile.currentSemesterGpa())
                + "\nTín chỉ tích lũy: " + displayNumber(profile.accumulatedCredits())
                + "\nTín chỉ yêu cầu tốt nghiệp: " + displayNumber(profile.requiredGraduationCredits())
                + "\nTín chỉ đăng ký kỳ hiện tại: " + displayNumber(profile.currentRegisteredCredits())
                + "\nMục tiêu GPA tốt nghiệp đã lưu: "
                + (profile.graduationTargetGpa() == null ? "chưa đặt" : displayNumber(profile.graduationTargetGpa()))
                + "\nSố học phần đã có kết quả trong kỳ: " + profile.currentSemesterGradedCourseCount()
                + "/" + profile.currentSemesterCourseCount()
                + (profile.historyComplete() ? "" : "\nChưa đủ lịch sử để kết luận số liệu tích lũy toàn khóa.")
                + "\n" + Objects.requireNonNullElse(profile.scopeNotice(), "");
    }

    private String displayNumber(Number number) {
        if (number == null) return "chưa đủ dữ liệu";
        return number instanceof BigDecimal decimal ? decimal.toPlainString().replace('.', ',') : number.toString();
    }

    private record RequestIdentity(Long userId, UUID clientRequestId) {}
}
