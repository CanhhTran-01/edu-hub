package com.eduhub.edu_hub.aiassistant.application.service;

import com.eduhub.edu_hub.aiassistant.api.dto.request.ChatRequest;
import com.eduhub.edu_hub.aiassistant.api.dto.response.ChatResponse;
import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.aiassistant.application.port.AiAssistantPort;
import com.eduhub.edu_hub.aiassistant.application.port.AiAssistantPort.ConversationMessage;
import com.eduhub.edu_hub.aiassistant.application.port.KnowledgeChunkPort.RetrievedChunk;
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
    private final AiAccessService accessService;
    private final AiAssistantPort assistantPort;
    private final AcademicKnowledgeSearchService knowledgeSearchService;
    private final ChatHistoryService historyService;
    private final AiRagProperties properties;
    private final AiTokenCounter tokenCounter;
    private final AiQuestionRateLimiter questionRateLimiter;
    private final AsyncTaskExecutor executor;

    public AiChatService(
            AiAccessService accessService,
            AiAssistantPort assistantPort,
            AcademicKnowledgeSearchService knowledgeSearchService,
            ChatHistoryService historyService,
            AiRagProperties properties,
            AiTokenCounter tokenCounter,
            AiQuestionRateLimiter questionRateLimiter,
            @Qualifier("aiRequestExecutor") AsyncTaskExecutor executor) {
        this.accessService = accessService;
        this.assistantPort = assistantPort;
        this.knowledgeSearchService = knowledgeSearchService;
        this.historyService = historyService;
        this.properties = properties;
        this.tokenCounter = tokenCounter;
        this.questionRateLimiter = questionRateLimiter;
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
        questionRateLimiter.consumeQuestionAllowance(userId);
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
        return historyService.recordExchange(principal, context, question, answer.content(), answer.citedChunks());
    }

    private PreparedAnswer prepareAnswer(String question, List<ConversationMessage> history) {
        // NOTE: Endpoint RAG đồng bộ vẫn truy xuất câu hỏi trực tiếp để giữ contract hiện có.
        // Agent ở /chat/stream tự dựng query hoặc hỏi rõ qua searchAcademicKnowledge, không gọi lại use-case lưu chat.
        List<RetrievedChunk> chunks = knowledgeSearchService.findRelevantKnowledge(question);
        if (chunks.isEmpty()) return new PreparedAnswer(properties.getFallbackMessage(), List.of());

        // Tập này được dùng cả khi dựng prompt và hậu kiểm; không truy xuất lần hai để suy ra nguồn của câu trả lời.
        var answer = assistantPort.answer(question, history, chunks);
        Set<Long> suppliedChunkIds =
                chunks.stream().map(RetrievedChunk::chunkId).collect(Collectors.toSet());
        if (answer == null
                || !AiContentPolicy.hasValidCitations(answer.content(), answer.citationIds(), suppliedChunkIds)
                || answer.content().length() > 20000) {
            return new PreparedAnswer(properties.getFallbackMessage(), List.of());
        }
        Set<Long> citedChunkIds = new HashSet<>(answer.citationIds());
        return new PreparedAnswer(
                AiContentPolicy.redactPromptText(answer.content()),
                chunks.stream()
                        .filter(chunk -> citedChunkIds.contains(chunk.chunkId()))
                        .toList());
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

    private AiAssistantException serviceUnavailable(Long userId) {
        log.warn("Dịch vụ AI lỗi hoặc quá thời hạn: actorId={}", userId);
        return new AiAssistantException(ErrorCode.AI_SERVICE_UNAVAILABLE);
    }

    private record PreparedAnswer(String content, List<RetrievedChunk> citedChunks) {}
}
