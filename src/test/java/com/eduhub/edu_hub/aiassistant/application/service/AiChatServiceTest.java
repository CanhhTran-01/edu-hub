package com.eduhub.edu_hub.aiassistant.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.aiassistant.api.dto.request.ChatRequest;
import com.eduhub.edu_hub.aiassistant.api.dto.response.ChatResponse;
import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.aiassistant.application.port.*;
import com.eduhub.edu_hub.aiassistant.application.port.KnowledgeChunkPort.RetrievedChunk;
import com.eduhub.edu_hub.aiassistant.domain.enums.AiIntent;
import com.eduhub.edu_hub.aiassistant.infrastructure.adapter.AiTokenCounter;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiRagProperties;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.security.oauth2.jwt.Jwt;

class AiChatServiceTest {
    AiAccessService accessService = mock(AiAccessService.class);
    AiAssistantPort assistantPort = mock(AiAssistantPort.class);
    KnowledgeChunkPort chunkPort = mock(KnowledgeChunkPort.class);
    ChatHistoryService historyService = mock(ChatHistoryService.class);
    AiTokenCounter tokenCounter = mock(AiTokenCounter.class);
    AsyncTaskExecutor executor = mock(AsyncTaskExecutor.class);
    AiRagProperties properties = new AiRagProperties();
    Clock clock = Clock.fixed(Instant.parse("2026-11-18T17:30:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    Jwt principal = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("student")
            .build();
    ChatHistoryService.ConversationContext context = new ChatHistoryService.ConversationContext(null, null, List.of());
    AiChatService service;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void prepareService() {
        properties.setFallbackMessage("Chưa đủ căn cứ.");
        properties.setQuestionsPerMinute(10);
        when(accessService.requireStudent(principal))
                .thenReturn(UserInfoResponse.builder().id(7L).build());
        when(historyService.loadConversation(7L, null)).thenReturn(context);
        when(executor.submit(any(Callable.class))).thenAnswer(invocation -> {
            var task = new FutureTask((Callable) invocation.getArgument(0));
            task.run();
            return task;
        });
        when(assistantPort.analyzeQuestion(anyString(), anyList()))
                .thenReturn(new AiAssistantPort.QuestionAnalysis(AiIntent.KNOWLEDGE, "Quy chế học lại?"));
        when(historyService.recordExchange(any(), any(), anyString(), any(), anyString(), anyList()))
                .thenReturn(ChatResponse.builder().content("Đã lưu").build());
        service = new AiChatService(
                accessService, assistantPort, chunkPort, historyService, properties, tokenCounter, clock, executor);
    }

    @Test
    void noRelevantChunkReturnsKnowledgeFallbackWithoutCallingAnswerModel() {
        when(chunkPort.findRelevantChunks(anyString(), eq(LocalDate.of(2026, 11, 19))))
                .thenReturn(List.of());
        service.chat(principal, request("Học lại thế nào?"));
        verify(assistantPort, never()).answer(anyString(), anyList(), anyList());
        verify(historyService)
                .recordExchange(
                        principal, context, "Học lại thế nào?", AiIntent.KNOWLEDGE, "Chưa đủ căn cứ.", List.of());
    }

    @Test
    void savesOnlyReferencesActuallyCitedFromSuppliedContext() {
        RetrievedChunk first = chunk(12L);
        RetrievedChunk second = chunk(13L);
        when(chunkPort.findRelevantChunks(anyString(), any())).thenReturn(List.of(first, second));
        when(assistantPort.answer(anyString(), anyList(), anyList()))
                .thenReturn(new AiAssistantPort.Answer("Theo quy chế [K13].", List.of(13L)));
        service.chat(principal, request("Học lại thế nào?"));
        verify(historyService)
                .recordExchange(
                        principal,
                        context,
                        "Học lại thế nào?",
                        AiIntent.KNOWLEDGE,
                        "Theo quy chế [K13].",
                        List.of(second));
    }

    @Test
    void fabricatedReferenceBecomesFallbackRatherThanSavedKnowledge() {
        when(chunkPort.findRelevantChunks(anyString(), any())).thenReturn(List.of(chunk(12L)));
        when(assistantPort.answer(anyString(), anyList(), anyList()))
                .thenReturn(new AiAssistantPort.Answer("Theo nguồn [K999].", List.of(999L)));
        service.chat(principal, request("Học lại thế nào?"));
        verify(historyService)
                .recordExchange(
                        principal, context, "Học lại thế nào?", AiIntent.KNOWLEDGE, "Chưa đủ căn cứ.", List.of());
    }

    @Test
    void outOfScopeQuestionDoesNotSearchVectorStore() {
        when(assistantPort.analyzeQuestion(anyString(), anyList()))
                .thenReturn(new AiAssistantPort.QuestionAnalysis(AiIntent.OUT_OF_SCOPE, ""));
        service.chat(principal, request("Dự báo chứng khoán?"));
        verifyNoInteractions(chunkPort);
        verify(assistantPort, never()).answer(anyString(), anyList(), anyList());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void timeoutCancelsTaskAndDoesNotSaveMessagesOrEmptySession() throws Exception {
        Future timedOutTask = mock(Future.class);
        when(timedOutTask.get(anyLong(), eq(TimeUnit.MILLISECONDS))).thenThrow(new TimeoutException());
        when(executor.submit(any(Callable.class))).thenReturn(timedOutTask);
        assertThatThrownBy(() -> service.chat(principal, request("Học lại?")))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE));
        verify(timedOutTask).cancel(true);
        verify(historyService, never()).recordExchange(any(), any(), anyString(), any(), anyString(), anyList());
    }

    @Test
    void rateLimitIsCheckedPerAuthenticatedAccountBeforeLoadingHistoryAgain() {
        properties.setQuestionsPerMinute(1);
        when(chunkPort.findRelevantChunks(anyString(), any())).thenReturn(List.of());
        service.chat(principal, request("Học lại?"));
        assertThatThrownBy(() -> service.chat(principal, request("Học tiếp?")))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.AI_RATE_LIMIT_EXCEEDED));
        verify(historyService, times(1)).loadConversation(7L, null);
    }

    private ChatRequest request(String question) {
        var request = new ChatRequest();
        request.setMessage(question);
        return request;
    }

    @Test
    void unresolvedFollowUpAsksForClarificationWithoutSearchingOrGuessing() {
        when(assistantPort.analyzeQuestion(anyString(), anyList()))
                .thenReturn(new AiAssistantPort.QuestionAnalysis(AiIntent.KNOWLEDGE, ""));
        service.chat(principal, request("Còn môn đó thì sao?"));
        verifyNoInteractions(chunkPort);
        verify(historyService)
                .recordExchange(
                        eq(principal),
                        eq(context),
                        eq("Còn môn đó thì sao?"),
                        eq(AiIntent.KNOWLEDGE),
                        contains("nêu rõ"),
                        eq(List.of()));
    }

    private RetrievedChunk chunk(Long chunkId) {
        return new RetrievedChunk(chunkId, 1L, "Quy chế", null, "Quy định học lại.", 20, 0.9, LocalDate.of(2026, 1, 1));
    }
}
