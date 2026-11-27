package com.eduhub.edu_hub.aiassistant.application.service;

import com.eduhub.edu_hub.academic.api.dto.response.AdvisorAcademicProfileResponse;
import com.eduhub.edu_hub.academic.api.dto.response.GpaGoalResponse;
import com.eduhub.edu_hub.academic.api.dto.response.PrerequisiteImpactResponse;
import com.eduhub.edu_hub.academic.domain.enums.AcademicRequirementStatus;
import com.eduhub.edu_hub.academic.domain.enums.GpaGoalStatus;
import com.eduhub.edu_hub.academic.domain.enums.PrerequisiteAnalysisStatus;
import com.eduhub.edu_hub.aiassistant.api.dto.request.AdvisorChatRequest;
import com.eduhub.edu_hub.aiassistant.api.dto.response.AdvisorMessagePayloadResponse;
import com.eduhub.edu_hub.aiassistant.api.dto.response.AdvisorRequestStatusResponse;
import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.aiassistant.application.port.*;
import com.eduhub.edu_hub.aiassistant.application.port.AiAdvisorModelPort.*;
import com.eduhub.edu_hub.aiassistant.application.service.AdvisorChatExecution.AdvisorExecutionStoppedException;
import com.eduhub.edu_hub.aiassistant.domain.policy.AiContentPolicy;
import com.eduhub.edu_hub.aiassistant.infrastructure.adapter.AiTokenCounter;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.*;
import com.eduhub.edu_hub.enrollment.api.dto.response.MinimumExamScoreResponse;
import com.eduhub.edu_hub.enrollment.domain.enums.MinimumExamScoreStatus;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
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
    private static final int MAXIMUM_ACADEMIC_TEXT_CHARACTERS = 18000;
    private static final String CAPABILITIES_MESSAGE =
            "Xin chào! Hiện tôi hỗ trợ tra cứu quy chế, xem tổng quan học vụ, tìm phương án GPA kỳ hiện tại, "
                    + "phân tích tiên quyết và tính điểm thi tối thiểu từ TP1/TP2 bạn nhập. Bạn cần hỗ trợ nội dung nào?";
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
    private final Semaphore capacity;
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
            @Qualifier("aiAdvisorLifecycleScheduler") TaskScheduler scheduler,
            @Qualifier("aiAdvisorCapacity") Semaphore capacity) {
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
        this.capacity = capacity;
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
            if (!capacity.tryAcquire()) throw new AiAssistantException(ErrorCode.AI_ADVISOR_CAPACITY_EXCEEDED);
            try {
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
            } catch (RuntimeException exception) {
                capacity.release();
                throw exception;
            }
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
            boolean isClarification = isSafeClarification(modelAnswer);
            if (execution.getGpaGoal() != null) {
                content = renderGpaGoal(execution.getGpaGoal());
                isClarification = execution.getGpaGoal().status() == GpaGoalStatus.NEEDS_CLARIFICATION;
            } else if (execution.hasAttemptedGpaGoalSearch()) {
                content = "Chưa thể xác minh phương án GPA kỳ hiện tại. "
                        + "Hãy kiểm tra kỳ học/dữ liệu khả dụng rồi yêu cầu tính lại; chưa có căn cứ kết luận không thể đạt.";
                isClarification = false;
            } else if (isClarification) {
                content = AiContentPolicy.redactPromptText(modelAnswer.content());
            } else if (execution.getAcademicProfile() != null) {
                // Chỉ template BE phát số liệu cá nhân; text model không được tự thêm GPA/tín chỉ.
                content = renderAcademicProfile(execution.getAcademicProfile());

                // Sử dụng câu trả lời của AI nếu nội dung và trích dẫn hợp lệ.
            } else if (hasValidKnowledgeAnswer) {
                content = AiContentPolicy.redactPromptText(modelAnswer.content());

                // Xử lý trường hợp AI chưa thực thi bất kỳ Tool nào.
            } else if (execution.getStatus().toolCalls() == 0) {
                // Chỉ nhận câu hỏi làm rõ không chứa số khi chưa có bằng chứng; còn lại giới thiệu capability thật.
                content = CAPABILITIES_MESSAGE;

                // Sử dụng câu trả lời fallback khi không có đủ bằng chứng hợp lệ.
            } else {
                content = ragProperties.getFallbackMessage();
            }

            boolean hasPrerequisiteAnswer =
                    execution.getPrerequisiteImpact() != null || execution.hasAttemptedPrerequisiteAnalysis();
            boolean hasExamAnswer = execution.getExamScore() != null || execution.hasAttemptedExamScoreCalculation();
            if (hasPrerequisiteAnswer || hasExamAnswer) {
                var academicSections = new ArrayList<String>();
                boolean hasGpaAnswer = execution.getGpaGoal() != null || execution.hasAttemptedGpaGoalSearch();
                if (hasGpaAnswer) academicSections.add(content);
                isClarification = hasGpaAnswer && isClarification;
                if (hasPrerequisiteAnswer) {
                    var prerequisiteImpact = execution.getPrerequisiteImpact();
                    academicSections.add(
                            prerequisiteImpact == null
                                    ? "Chưa thể xác minh phân tích tiên quyết từ dữ liệu hiện hành."
                                    : renderPrerequisiteImpact(prerequisiteImpact));
                    isClarification |= prerequisiteImpact != null
                            && prerequisiteImpact.status() == PrerequisiteAnalysisStatus.NEEDS_CLARIFICATION;
                }
                if (hasExamAnswer) {
                    var examScore = execution.getExamScore();
                    academicSections.add(
                            examScore == null
                                    ? "Chưa thể xác minh điểm thi tối thiểu theo thang điểm hiện hành."
                                    : renderExamScore(examScore));
                    isClarification |=
                            examScore != null && examScore.status() == MinimumExamScoreStatus.NEEDS_CLARIFICATION;
                }
                content = String.join("\n\n", academicSections);
                if (content.length() > MAXIMUM_ACADEMIC_TEXT_CHARACTERS) {
                    int end = content.offsetByCodePoints(
                            0, Math.min(MAXIMUM_ACADEMIC_TEXT_CHARACTERS, content.codePointCount(0, content.length())));
                    content = content.substring(0, end) + "\nXem đầy đủ các hàng và chuỗi môn trong bảng kết quả.";
                }
            }

            // 6. Hoàn tất execution và lưu câu trả lời đã xác minh vào lịch sử hội thoại.
            var payload = execution.getGpaGoal() != null
                            || execution.getAcademicProfile() != null
                            || isClarification
                            || hasPrerequisiteAnswer
                            || hasExamAnswer
                    ? new AdvisorMessagePayloadResponse(
                            AdvisorMessagePayloadResponse.SCHEMA_VERSION,
                            AdvisorMessagePayloadResponse.TYPE,
                            content,
                            isClarification,
                            execution.getAcademicProfile(),
                            execution.getGpaGoal(),
                            execution.getPrerequisiteImpact(),
                            execution.getExamScore())
                    : null;
            if (payload != null && !citedChunks.isEmpty())
                content += "\nTài liệu liên quan: "
                        + citedChunks.stream()
                                .map(chunk -> "[K" + chunk.chunkId() + "]")
                                .collect(Collectors.joining(", "))
                        + ".";
            String verifiedContent = content;
            execution.complete(() -> payload == null
                    ? historyService.recordExchange(
                            execution.getPrincipal(),
                            execution.getConversation(),
                            execution.getQuestion(),
                            verifiedContent,
                            citedChunks)
                    : historyService.recordExchange(
                            execution.getPrincipal(),
                            execution.getConversation(),
                            execution.getQuestion(),
                            verifiedContent,
                            citedChunks,
                            payload));

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
        if (activeRequestsByUserId.remove(execution.getUserId(), execution)) capacity.release();
    }

    private void removeExpiredRequests() {
        // TODO(ADVISOR_REQUEST_PERSISTENCE): lưu trạng thái/idempotency bền để phục hồi sau restart.
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

    private boolean isSafeClarification(AiAdvisorModelPort.Answer answer) {
        return answer != null
                && answer.content() != null
                && !answer.content().isBlank()
                && answer.content().length() <= 2000
                && answer.content().strip().endsWith("?")
                && answer.content().codePoints().noneMatch(Character::isDigit)
                && !answer.content().contains("<")
                && !answer.content().contains(">")
                && !answer.content().contains("```")
                && answer.citationIds() != null
                && answer.citationIds().isEmpty();
    }

    private String renderGpaGoal(GpaGoalResponse goal) {
        var text = new StringBuilder("Kỳ tính: ")
                .append(goal.snapshot().semester().getSemesterName())
                .append('\n')
                .append(goal.message());
        if (goal.snapshot().semesterTargetGpa() != null)
            text.append("\nMục tiêu GPA kỳ đã dùng: ")
                    .append(displayNumber(goal.snapshot().semesterTargetGpa()));
        if (!goal.conditions().isEmpty()) {
            text.append("\nĐiều kiện đang áp dụng:");
            for (var condition : goal.conditions()) {
                String comparison =
                        switch (condition.comparison()) {
                            case EXACT -> "đúng";
                            case AT_MOST -> "tối đa";
                            case AT_LEAST -> "ít nhất";
                            default -> "cần làm rõ";
                        };
                text.append("\n- ")
                        .append(condition.courseReference())
                        .append(": ")
                        .append(comparison)
                        .append(' ')
                        .append(condition.gradeLetter());
            }
        }
        if (goal.preview() != null) {
            var preview = goal.preview();
            text.append("\nGPA kỳ theo phương án: ").append(displayNumber(preview.semesterGpa()));
            for (var course : preview.courses().stream().limit(50).toList()) {
                String letters = String.join("/", course.gradeLetters());
                var exact = goal.conditions().stream()
                        .filter(condition -> condition.enrollmentId().equals(course.enrollmentId())
                                && condition.comparison()
                                        == com.eduhub.edu_hub.academic.domain.enums.CourseGradeComparison.EXACT)
                        .findFirst();
                if (exact.isPresent() && !course.hasPublishedGrade())
                    letters = exact.get().gradeLetter();
                text.append("\n- ")
                        .append(course.subjectCode())
                        .append(" — ")
                        .append(course.subjectName())
                        .append(": ")
                        .append(letters.isBlank() ? displayNumber(course.grade4()) : letters)
                        .append(" (")
                        .append(displayNumber(course.grade4()))
                        .append(", ")
                        .append(course.hasPublishedGrade() ? "điểm đã công bố" : "giả định")
                        .append(')');
            }
            if (preview.courses().size() > 50) text.append("\nXem đầy đủ các môn trong bảng phương án.");
            if (preview.courses().stream().anyMatch(course -> course.grade4().signum() == 0))
                text.append(
                        "\nPhương án vẫn có F do điểm đã công bố hoặc điều kiện bắt buộc; không đồng nghĩa mọi môn đều đạt.");
            if (preview.cumulativeGpa() != null)
                text.append("\nGPA tích lũy dự kiến đến cuối kỳ: ").append(displayNumber(preview.cumulativeGpa()));
            if (!preview.historyComplete()) text.append('\n').append(preview.scopeNotice());
            text.append("\nĐây là phương án giả định, chưa phải điểm thực và chưa chứng minh mục tiêu tốt nghiệp.");
        }
        if (goal.status() == GpaGoalStatus.INFEASIBLE)
            text.append("\nGPA kỳ tối đa theo các điều kiện: ")
                    .append(displayNumber(goal.maximumPossibleSemesterGpa()));
        return text.toString();
    }

    private String renderPrerequisiteImpact(PrerequisiteImpactResponse response) {
        var text = new StringBuilder("Phân tích tiên quyết");
        if (response.semester() != null)
            text.append(" — ").append(response.semester().getSemesterName());
        text.append('\n').append(response.message());
        for (var course : response.courses().stream().limit(50).toList()) {
            text.append("\n\n")
                    .append(Objects.requireNonNullElse(course.subject().subjectCode(), "Môn chưa rõ mã"))
                    .append(" — ")
                    .append(course.subject().subjectName())
                    .append(
                            course.registeredInCurrentSemester()
                                    ? " (đăng ký kỳ hiện tại)"
                                    : " (môn CTĐT, không phải lượt đăng ký kỳ này)")
                    .append("\nTiên quyết trước kỳ: ")
                    .append(requirementStatusLabel(course.prerequisiteStatus()));
            if (course.prerequisites().isEmpty())
                text.append("\nKhông có cạnh tiên quyết được cung cấp trong phạm vi dữ liệu.");
            else
                for (var requirement : course.prerequisites())
                    text.append("\n- ")
                            .append(Objects.requireNonNullElse(
                                    requirement.subject().subjectCode(),
                                    requirement.subject().subjectName()))
                            .append(": ")
                            .append(
                                    requirement.passedBeforeSemester() == null
                                            ? "chưa đủ lịch sử"
                                            : requirement.passedBeforeSemester()
                                                    ? "đã đạt trước kỳ"
                                                    : "chưa đạt trước kỳ");
            if (!course.failureAssumptionApplicable()) {
                text.append("\nĐã có kết quả đạt; giả định trượt không làm mất môn tiên quyết đã hoàn thành.");
            } else {
                text.append("\nẢnh hưởng khi không đạt: ").append(requirementStatusLabel(course.impactStatus()));
                appendDependencyPaths(text, "Trực tiếp", course.directImpacts());
                appendDependencyPaths(text, "Gián tiếp", course.indirectImpacts());
                if (course.impactStatus() == AcademicRequirementStatus.SATISFIED
                        && course.directImpacts().isEmpty()
                        && course.indirectImpacts().isEmpty())
                    text.append("\nKhông thấy môn chưa đạt nào bị ảnh hưởng trong phạm vi đã xác minh.");
            }
        }
        if (!response.historyComplete() || !response.graphComplete())
            text.append('\n').append(response.scopeNotice());
        if (response.status() == PrerequisiteAnalysisStatus.SEARCH_LIMIT_REACHED)
            text.append("\nĐã chạm giới hạn số môn/chuỗi cần duyệt; phần chưa xét không được coi là thỏa mãn.");
        return text.toString();
    }

    private void appendDependencyPaths(
            StringBuilder text, String label, List<PrerequisiteImpactResponse.DependencyImpactResponse> impacts) {
        for (var impact : impacts.stream().limit(20).toList())
            text.append("\n- ")
                    .append(label)
                    .append(": ")
                    .append(impact.path().stream()
                            .map(subject -> Objects.requireNonNullElse(subject.subjectCode(), subject.subjectName()))
                            .collect(Collectors.joining(" → ")))
                    .append(
                            impact.status() == AcademicRequirementStatus.INSUFFICIENT_DATA
                                    ? " (chưa đủ lịch sử để xác nhận)"
                                    : "");
        if (impacts.size() > 20) text.append("\nXem thêm các chuỗi trong bảng kết quả.");
    }

    private String requirementStatusLabel(AcademicRequirementStatus status) {
        return switch (status) {
            case SATISFIED -> "Thỏa mãn trong phạm vi đã xác minh";
            case BLOCKED -> "Vướng tiên quyết";
            case INSUFFICIENT_DATA -> "Chưa đủ dữ liệu";
        };
    }

    private String renderExamScore(MinimumExamScoreResponse response) {
        var text = new StringBuilder("Tính điểm thi tối thiểu — ")
                .append(Objects.requireNonNullElse(response.subjectName(), "chưa điền tên môn"))
                .append('\n')
                .append(response.message());
        if (response.componentScore1() != null)
            text.append("\nTP1 đã nhập: ").append(displayNumber(response.componentScore1()));
        if (response.componentScore2() != null)
            text.append("\nTP2 đã nhập: ").append(displayNumber(response.componentScore2()));
        if (response.targetGradeLetter() != null)
            text.append("\nMục tiêu: từ ").append(response.targetGradeLetter()).append(" trở lên");
        if (response.processScore() != null)
            text.append("\nĐiểm quá trình sau làm tròn: ").append(displayNumber(response.processScore()));
        if (response.status() == MinimumExamScoreStatus.SOLVED) {
            text.append("\nĐiểm thi tối thiểu (bước 0,001): ")
                    .append(displayNumber(response.minimumExamScore()))
                    .append("\nNếu chỉ nhập hai chữ số thập phân: ")
                    .append(displayNumber(response.minimumExamScoreTwoDecimals()))
                    .append("\nTổng kết tại ngưỡng tối thiểu: ")
                    .append(displayNumber(response.achievedCourseScore10()))
                    .append(" — ")
                    .append(response.achievedGradeLetter());
        } else if (response.status() == MinimumExamScoreStatus.UNREACHABLE) {
            text.append("\nTổng kết tối đa khi thi 10: ")
                    .append(displayNumber(response.maximumCourseScore10()))
                    .append(" — ")
                    .append(response.maximumGradeLetter());
        }
        text.append("\nĐây là tính thử từ TP1/TP2 đã nhập; không thay đổi điểm thật.");
        return text.toString();
    }

    private String renderAcademicProfile(AdvisorAcademicProfileResponse profile) {
        return "GPA tích lũy: " + displayNumber(profile.cumulativeGpa())
                + "\nGPA kỳ hiện tại: " + displayNumber(profile.currentSemesterGpa())
                + "\nTín chỉ tích lũy: " + displayNumber(profile.accumulatedCredits())
                + "\nTín chỉ yêu cầu tốt nghiệp: " + displayNumber(profile.requiredGraduationCredits())
                + "\nTín chỉ đăng ký kỳ hiện tại: " + displayNumber(profile.currentRegisteredCredits())
                + "\nMục tiêu GPA tốt nghiệp đã lưu: "
                + (profile.graduationTargetGpa() == null ? "chưa đặt" : displayNumber(profile.graduationTargetGpa()))
                + "\nMục tiêu GPA kỳ mặc định đã lưu: "
                + (profile.semesterTargetGpa() == null ? "chưa đặt" : displayNumber(profile.semesterTargetGpa()))
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
