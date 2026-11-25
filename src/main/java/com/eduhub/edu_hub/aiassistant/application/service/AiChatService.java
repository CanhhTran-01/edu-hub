package com.eduhub.edu_hub.aiassistant.application.service;

import com.eduhub.edu_hub.aiassistant.api.dto.request.ChatRequest;
import com.eduhub.edu_hub.aiassistant.api.dto.response.ChatResponse;
import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.aiassistant.application.port.*;
import com.eduhub.edu_hub.aiassistant.application.port.AiAssistantPort.ConversationMessage;
import com.eduhub.edu_hub.aiassistant.application.port.KnowledgeChunkPort.RetrievedChunk;
import com.eduhub.edu_hub.aiassistant.domain.enums.AiIntent;
import com.eduhub.edu_hub.aiassistant.domain.policy.AiContentPolicy;
import com.eduhub.edu_hub.aiassistant.infrastructure.adapter.AiTokenCounter;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiRagProperties;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AiChatService {
    private static final int MAXIMUM_QUESTION_LENGTH = 2000;
    private static final int MAXIMUM_REWRITTEN_QUESTION_LENGTH = 4000;
    private static final int MAXIMUM_TRACKED_USERS = 100000;
    private final AiAccessService accessService;
    private final AiAssistantPort assistantPort;
    private final KnowledgeChunkPort chunkPort;
    private final ChatHistoryService historyService;
    private final AiRagProperties properties;
    private final AiTokenCounter tokenCounter;
    private final Clock clock;
    private final AsyncTaskExecutor executor;
    private final Map<Long, RateWindow> rateWindows = new ConcurrentHashMap<>();

    public AiChatService(
            AiAccessService accessService,
            AiAssistantPort assistantPort,
            KnowledgeChunkPort chunkPort,
            ChatHistoryService historyService,
            AiRagProperties properties,
            AiTokenCounter tokenCounter,
            Clock clock,
            @Qualifier("aiRequestExecutor") AsyncTaskExecutor executor) {
        this.accessService = accessService;
        this.assistantPort = assistantPort;
        this.chunkPort = chunkPort;
        this.historyService = historyService;
        this.properties = properties;
        this.tokenCounter = tokenCounter;
        this.clock = clock;
        this.executor = executor;
    }

    public ChatResponse chat(Jwt principal, ChatRequest request) {
        Long userId = accessService.requireStudent(principal).getId();
        if (request == null
                || request.getMessage() == null
                || request.getMessage().isBlank()
                || request.getMessage().length() > MAXIMUM_QUESTION_LENGTH) {
            throw new AiAssistantException(ErrorCode.VALIDATION_ERROR);
        }
        consumeQuestionAllowance(userId);
        String question = AiContentPolicy.redactPromptText(AiContentPolicy.normalizeQuestion(request.getMessage()));
        var context = historyService.loadConversation(userId, request.getSessionId());
        List<ConversationMessage> history = limitHistory(context.messages());
        Future<PreparedAnswer> future = null;
        PreparedAnswer answer;
        try {
            future = executor.submit(() -> prepareAnswer(question, history));
            answer = future.get(properties.getRequestTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw serviceUnavailable(userId);
        } catch (ExecutionException | TimeoutException | RejectedExecutionException exception) {
            if (future != null) future.cancel(true);
            throw serviceUnavailable(userId);
        }
        // TODO: Truy vấn dữ liệu cá nhân và tính kịch bản tốt nghiệp chỉ bổ sung qua facade/công cụ có ownership.
        return historyService.recordExchange(
                principal, context, question, answer.intent(), answer.content(), answer.citedChunks());
    }

    private PreparedAnswer prepareAnswer(String question, List<ConversationMessage> history) {
        var analysis = assistantPort.analyzeQuestion(question, history);
        if (analysis == null || analysis.intent() == null) throw new IllegalStateException("AI không trả phân loại");
        if (analysis.intent() == AiIntent.OUT_OF_SCOPE) {
            return new PreparedAnswer(AiIntent.OUT_OF_SCOPE, properties.getOutOfScopeMessage(), List.of());
        }
        if (analysis.standaloneQuestion() == null
                || analysis.standaloneQuestion().length() > MAXIMUM_REWRITTEN_QUESTION_LENGTH) {
            throw new IllegalStateException("AI không trả câu hỏi độc lập hợp lệ");
        }
        if (analysis.standaloneQuestion().isBlank()) {
            return new PreparedAnswer(
                    AiIntent.KNOWLEDGE,
                    "Bạn vui lòng nêu rõ học phần hoặc nội dung quy chế muốn hỏi để tôi tra cứu chính xác.",
                    List.of());
        }
        String standaloneQuestion =
                AiContentPolicy.redactPromptText(AiContentPolicy.normalizeQuestion(analysis.standaloneQuestion()));
        List<RetrievedChunk> chunks =
                selectContext(chunkPort.findRelevantChunks(standaloneQuestion, LocalDate.now(clock)));
        if (chunks.isEmpty()) return new PreparedAnswer(AiIntent.KNOWLEDGE, properties.getFallbackMessage(), List.of());

        // Tập này được dùng cả khi dựng prompt và hậu kiểm; không truy xuất lần hai để suy ra nguồn của câu trả lời.
        var answer = assistantPort.answer(standaloneQuestion, history, chunks);
        Set<Long> suppliedChunkIds =
                chunks.stream().map(RetrievedChunk::chunkId).collect(Collectors.toSet());
        if (answer == null
                || !AiContentPolicy.hasValidCitations(answer.content(), answer.citationIds(), suppliedChunkIds)
                || answer.content().length() > 20000) {
            return new PreparedAnswer(AiIntent.KNOWLEDGE, properties.getFallbackMessage(), List.of());
        }
        Set<Long> citedChunkIds = new HashSet<>(answer.citationIds());
        return new PreparedAnswer(
                AiIntent.KNOWLEDGE,
                AiContentPolicy.redactPromptText(answer.content()),
                chunks.stream()
                        .filter(chunk -> citedChunkIds.contains(chunk.chunkId()))
                        .toList());
    }

    private List<RetrievedChunk> selectContext(List<RetrievedChunk> candidates) {
        List<RetrievedChunk> selectedChunks = new ArrayList<>();
        int contextTokens = 0;
        for (RetrievedChunk chunk : candidates) {
            int chunkTokens =
                    tokenCounter.countTokens(chunk.content() + chunk.sourceTitle() + chunk.effectiveDate()) + 64;
            if (selectedChunks.size() >= properties.getMaximumContextChunks()) break;
            if (contextTokens + chunkTokens > properties.getContextTokenLimit()) continue;
            selectedChunks.add(chunk);
            contextTokens += chunkTokens;
        }
        return List.copyOf(selectedChunks);
    }

    private List<ConversationMessage> limitHistory(List<ConversationMessage> messages) {
        List<ConversationMessage> selectedMessages = new ArrayList<>();
        int historyTokens = 0;
        for (int index = messages.size() - 1; index >= 0; index--) {
            var message = messages.get(index);
            String redactedContent = AiContentPolicy.redactPromptText(message.content());
            int messageTokens = tokenCounter.countTokens(redactedContent) + 16;
            if (historyTokens + messageTokens > properties.getMemoryTokenLimit()) break;
            selectedMessages.add(new ConversationMessage(message.role(), redactedContent));
            historyTokens += messageTokens;
        }
        Collections.reverse(selectedMessages);
        return List.copyOf(selectedMessages);
    }

    private void consumeQuestionAllowance(Long userId) {
        Instant now = clock.instant();
        rateWindows
                .entrySet()
                .removeIf(entry -> !entry.getValue().startedAt().plusSeconds(60).isAfter(now));
        if (!rateWindows.containsKey(userId) && rateWindows.size() >= MAXIMUM_TRACKED_USERS) {
            throw new AiAssistantException(ErrorCode.AI_RATE_LIMIT_EXCEEDED);
        }
        rateWindows.compute(userId, (accountId, previousWindow) -> {
            RateWindow window = previousWindow == null ? new RateWindow(now, 0) : previousWindow;
            if (window.questionCount() >= properties.getQuestionsPerMinute()) {
                throw new AiAssistantException(ErrorCode.AI_RATE_LIMIT_EXCEEDED);
            }
            return new RateWindow(window.startedAt(), window.questionCount() + 1);
        });
    }

    private AiAssistantException serviceUnavailable(Long userId) {
        log.warn("Dịch vụ AI lỗi hoặc quá thời hạn: actorId={}", userId);
        return new AiAssistantException(ErrorCode.AI_SERVICE_UNAVAILABLE);
    }

    private record PreparedAnswer(AiIntent intent, String content, List<RetrievedChunk> citedChunks) {}

    private record RateWindow(Instant startedAt, int questionCount) {}
}
