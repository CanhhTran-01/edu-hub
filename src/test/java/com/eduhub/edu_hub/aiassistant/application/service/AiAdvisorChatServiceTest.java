package com.eduhub.edu_hub.aiassistant.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.academic.api.dto.response.AdvisorAcademicProfileResponse;
import com.eduhub.edu_hub.aiassistant.api.dto.request.AdvisorChatRequest;
import com.eduhub.edu_hub.aiassistant.api.dto.response.ChatResponse;
import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.aiassistant.application.port.*;
import com.eduhub.edu_hub.aiassistant.domain.enums.AdvisorRequestState;
import com.eduhub.edu_hub.aiassistant.infrastructure.adapter.AiTokenCounter;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.*;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.oauth2.jwt.Jwt;

class AiAdvisorChatServiceTest {
    AiAccessService access = mock(AiAccessService.class);
    ChatHistoryService history = mock(ChatHistoryService.class);
    AiAdvisorModelPort model = mock(AiAdvisorModelPort.class);
    AiQuestionRateLimiter limiter = mock(AiQuestionRateLimiter.class);
    AiTokenCounter counter = mock(AiTokenCounter.class);
    AsyncTaskExecutor executor = mock(AsyncTaskExecutor.class);
    TaskScheduler scheduler = mock(TaskScheduler.class);
    AiAdvisorProperties settings = new AiAdvisorProperties();
    AiRagProperties ragSettings = new AiRagProperties();
    Clock clock = mock(Clock.class);
    Jwt principal =
            Jwt.withTokenValue("test").header("alg", "HS256").claim("uid", 7L).build();
    List<Runnable> workers = new ArrayList<>();
    List<String> events = new ArrayList<>();
    AiAdvisorChatService service;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void prepare() {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-25T17:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
        ragSettings.setFallbackMessage("Chưa đủ căn cứ.");
        when(access.requireStudent(any())).thenAnswer(invocation -> {
            Jwt jwt = invocation.getArgument(0);
            if (jwt == null) throw new AiAssistantException(ErrorCode.UNAUTHENTICATED);
            return UserInfoResponse.builder().id(jwt.getClaim("uid")).build();
        });
        when(history.loadConversation(anyLong(), any()))
                .thenReturn(new ChatHistoryService.ConversationContext(null, null, List.of()));
        when(history.recordExchange(any(), any(), anyString(), anyString(), anyList()))
                .thenAnswer(invocation -> ChatResponse.builder()
                        .sessionId("3")
                        .messageId("11")
                        .content(invocation.getArgument(3))
                        .citations(List.of())
                        .build());
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenReturn(mock(ScheduledFuture.class));
        when(scheduler.scheduleWithFixedDelay(any(Runnable.class), any(Duration.class)))
                .thenReturn(mock(ScheduledFuture.class));
        doAnswer(invocation -> {
                    workers.add(invocation.getArgument(0));
                    return null;
                })
                .when(executor)
                .execute(any(Runnable.class));
        service = new AiAdvisorChatService(
                access, history, model, limiter, settings, ragSettings, counter, clock, executor, scheduler);
    }

    @Test
    void studentCannotReadOrCancelAnotherStudentsRequest() {
        var execution = service.prepareChat(principal, request());
        var other = Jwt.withTokenValue("other")
                .header("alg", "HS256")
                .claim("uid", 8L)
                .build();
        assertNotFound(() -> service.findRequestStatus(other, execution.getRequestId()));
        assertNotFound(() -> service.cancelRequest(other, execution.getRequestId()));
        assertNotFound(() -> service.findRequestStatus(principal, UUID.randomUUID()));
        assertThat(service.findRequestStatus(principal, execution.getRequestId())
                        .state())
                .isEqualTo(AdvisorRequestState.RUNNING);
    }

    @Test
    void repeatedRequestAndConcurrentTurnDoNotStartAnotherModelOrConsumeRateAgain() {
        var request = request();
        var execution = service.prepareChat(principal, request);
        assertThatThrownBy(() -> service.prepareChat(principal, request))
                .isInstanceOfSatisfying(AiAssistantException.class, failure -> {
                    assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.AI_CHAT_REQUEST_CONFLICT);
                    assertThat(failure.getFieldErrors().getFirst().getMessage())
                            .isEqualTo(execution.getRequestId().toString());
                });
        assertThatThrownBy(() -> service.prepareChat(principal, request())).isInstanceOf(AiAssistantException.class);
        verify(limiter, times(1)).consumeQuestionAllowance(7L);
        verifyNoInteractions(model, executor);
    }

    @Test
    void cancellationBeforeWorkerStartsPreventsModelAndHistoryAndReleasesCapacityWhenWorkerExits() {
        var execution = start(request());
        assertThat(service.cancelRequest(principal, execution.getRequestId()).state())
                .isEqualTo(AdvisorRequestState.CANCELLED);
        workers.removeFirst().run();
        verifyNoInteractions(model);
        verify(history, never()).recordExchange(any(), any(), anyString(), anyString(), anyList());
        assertThat(events).containsExactly("agent.started", "agent.cancelled");
        assertThat(service.prepareChat(principal, request())).isNotNull();
    }

    @Test
    void resultArrivingAfterCancellationDoesNotBecomeSuccessfulHistory() {
        when(model.answer(any(), anyList())).thenAnswer(invocation -> {
            AdvisorChatExecution execution = invocation.getArgument(0);
            execution.cancel("USER_REQUEST");
            return new AiAdvisorModelPort.Answer("Kết quả đến muộn.", List.of());
        });
        var execution = start(request());
        workers.removeFirst().run();
        assertThat(execution.getStatus().state()).isEqualTo(AdvisorRequestState.CANCELLED);
        verify(history, never()).recordExchange(any(), any(), anyString(), anyString(), anyList());
        assertThat(events).containsExactly("agent.started", "agent.cancelled");
    }

    @Test
    void verifiedProfileReplacesInventedNumbersAndKeepsMissingHistoryDistinctFromZero() {
        when(model.answer(any(), anyList())).thenAnswer(invocation -> {
            AdvisorChatExecution execution = invocation.getArgument(0);
            execution.recordAcademicProfile(new AdvisorAcademicProfileResponse(
                    null,
                    null,
                    null,
                    BigDecimal.ZERO,
                    null,
                    120,
                    18,
                    BigDecimal.ZERO,
                    6,
                    1,
                    false,
                    "Chưa đủ lịch sử",
                    LocalDateTime.now(clock)));
            return new AiAdvisorModelPort.Answer("GPA của bạn là 9.99, đã có 999 tín chỉ.", List.of());
        });
        var execution = start(request());
        workers.removeFirst().run();
        assertThat(execution.getStatus().state()).isEqualTo(AdvisorRequestState.COMPLETED);
        assertThat(execution.getStatus().response().getContent())
                .contains("GPA tích lũy: chưa đủ dữ liệu", "GPA kỳ hiện tại: 0", "Mục tiêu GPA tốt nghiệp đã lưu: 0");
        assertThat(execution.getStatus().response().getContent()).doesNotContain("9.99", "999");
        assertThat(events).contains("ui.component", "context.updated", "text.delta");
        assertThat(events.getLast()).isEqualTo("agent.completed");
    }

    @Test
    void knowledgeAnswerCanOnlySaveCitationsFromToolsActuallyExecutedInThisTurn() {
        var chunk = new KnowledgeChunkPort.RetrievedChunk(
                21L, 3L, "Quy chế", null, "Nội dung", 10, 0.9, LocalDate.of(2026, 1, 1));
        when(model.answer(any(), anyList())).thenAnswer(invocation -> {
            AdvisorChatExecution execution = invocation.getArgument(0);
            execution.recordKnowledgeChunks(List.of(chunk));
            return new AiAdvisorModelPort.Answer("Theo quy chế [K21].", List.of(21L));
        });
        start(request());
        workers.removeFirst().run();
        verify(history)
                .recordExchange(eq(principal), any(), eq("Học vụ?"), eq("Theo quy chế [K21]."), eq(List.of(chunk)));
    }

    @Test
    void modelAndHistoryReceiveOnlyTheNormalizedRedactedQuestion() {
        var request = request();
        request.setMessage("  Quy chế   cho email sinhvien@example.com, CCCD 012345678901?  ");
        when(model.answer(any(), anyList())).thenReturn(new AiAdvisorModelPort.Answer("Xin chào", List.of()));
        var execution = start(request);

        workers.removeFirst().run();

        String redactedQuestion = "Quy chế cho email [đã ẩn email], CCCD [đã ẩn định danh]?";
        assertThat(execution.getQuestion()).isEqualTo(redactedQuestion);
        verify(model).answer(eq(execution), anyList());
        verify(history).recordExchange(eq(principal), any(), eq(redactedQuestion), anyString(), anyList());
    }

    @Test
    void emptyKnowledgeRetrievalSavesFallbackWithoutCitations() {
        when(model.answer(any(), anyList())).thenAnswer(invocation -> {
            AdvisorChatExecution execution = invocation.getArgument(0);
            execution.startTool("k1", "searchAcademicKnowledge", "{\"query\":\"Quy chế học lại?\"}", "Đang tra cứu");
            execution.recordKnowledgeChunks(List.of());
            execution.completeTool("k1", "searchAcademicKnowledge");
            return new AiAdvisorModelPort.Answer("Không tìm thấy nguồn phù hợp.", List.of());
        });
        var execution = start(request());

        workers.removeFirst().run();

        assertThat(execution.getStatus().state()).isEqualTo(AdvisorRequestState.COMPLETED);
        assertThat(execution.getStatus().response().getContent()).isEqualTo(ragSettings.getFallbackMessage());
        verify(history)
                .recordExchange(eq(principal), any(), anyString(), eq(ragSettings.getFallbackMessage()), eq(List.of()));
    }

    @Test
    void fabricatedKnowledgeReferencesUseFallbackWithoutSavingUnverifiedReferences() {
        var retrievedChunk = new KnowledgeChunkPort.RetrievedChunk(
                21L, 3L, "Quy chế", null, "Nội dung", 10, 0.9, LocalDate.of(2026, 1, 1));
        when(model.answer(any(), anyList())).thenAnswer(invocation -> {
            AdvisorChatExecution execution = invocation.getArgument(0);
            execution.startTool("k1", "searchAcademicKnowledge", "{\"query\":\"Quy chế học lại?\"}", "Đang tra cứu");
            execution.recordKnowledgeChunks(List.of(retrievedChunk));
            execution.completeTool("k1", "searchAcademicKnowledge");
            return new AiAdvisorModelPort.Answer("Theo quy chế [K999].", List.of(999L));
        });
        var execution = start(request());

        workers.removeFirst().run();

        assertThat(execution.getStatus().state()).isEqualTo(AdvisorRequestState.COMPLETED);
        assertThat(execution.getStatus().response().getContent()).isEqualTo(ragSettings.getFallbackMessage());
        verify(history)
                .recordExchange(eq(principal), any(), anyString(), eq(ragSettings.getFallbackMessage()), eq(List.of()));
    }

    @Test
    void noToolCannotInventPersonalAcademicFacts() {
        when(model.answer(any(), anyList()))
                .thenReturn(new AiAdvisorModelPort.Answer("Bạn đã tốt nghiệp loại Giỏi.", List.of()));
        var execution = start(request());
        workers.removeFirst().run();
        assertThat(execution.getStatus().response().getContent()).contains("Hiện tôi hỗ trợ");
        assertThat(execution.getStatus().response().getContent()).doesNotContain("đã tốt nghiệp");
    }

    @Test
    void foreignConversationIsRejectedBeforeAnyStreamOrModelStarts() {
        var request = request();
        request.setSessionId("999");
        when(history.loadConversation(7L, "999")).thenThrow(new AiAssistantException(ErrorCode.CHAT_SESSION_NOT_FOUND));
        assertThatThrownBy(() -> service.prepareChat(principal, request)).isInstanceOf(AiAssistantException.class);
        verifyNoInteractions(model, executor, scheduler, limiter);
    }

    @Test
    void unauthenticatedServiceCallDoesNotRelyOnSecurityFilter() {
        assertThatThrownBy(() -> service.prepareChat(null, request()))
                .isInstanceOfSatisfying(AiAssistantException.class, failure -> assertThat(failure.getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHENTICATED));
        verifyNoInteractions(history, model);
    }

    @Test
    void concurrencyIsBoundedAndAnOldCancelledWorkerStillHoldsItsSlotUntilItActuallyStops() {
        settings.setMaximumConcurrentRequests(1);
        var execution = start(request());
        service.cancelRequest(principal, execution.getRequestId());
        var other = Jwt.withTokenValue("other")
                .header("alg", "HS256")
                .claim("uid", 8L)
                .build();
        assertThatThrownBy(() -> service.prepareChat(other, request()))
                .isInstanceOfSatisfying(AiAssistantException.class, failure -> assertThat(failure.getErrorCode())
                        .isEqualTo(ErrorCode.AI_ADVISOR_CAPACITY_EXCEEDED));
        workers.removeFirst().run();
        assertThat(service.prepareChat(other, request())).isNotNull();
    }

    @Test
    void completedRuntimeRequestsExpireInsteadOfAccumulatingWithoutBound() {
        when(model.answer(any(), anyList())).thenReturn(new AiAdvisorModelPort.Answer("Xin chào", List.of()));
        var execution = start(request());
        workers.removeFirst().run();
        when(clock.instant()).thenReturn(Instant.parse("2026-11-25T17:11:00Z"));
        assertNotFound(() -> service.findRequestStatus(principal, execution.getRequestId()));
    }

    private AdvisorChatExecution start(AdvisorChatRequest request) {
        var execution = service.prepareChat(principal, request);
        execution.connect((name, envelope) -> events.add(name), () -> {});
        service.startChat(execution);
        return execution;
    }

    private AdvisorChatRequest request() {
        var request = new AdvisorChatRequest();
        request.setClientRequestId(UUID.randomUUID());
        request.setMessage("Học vụ?");
        return request;
    }

    private void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(AiAssistantException.class, failure -> assertThat(failure.getErrorCode())
                        .isEqualTo(ErrorCode.AI_CHAT_REQUEST_NOT_FOUND));
    }
}
