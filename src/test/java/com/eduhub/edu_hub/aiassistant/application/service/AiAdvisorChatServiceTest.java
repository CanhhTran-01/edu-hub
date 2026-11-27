package com.eduhub.edu_hub.aiassistant.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.academic.api.dto.response.AdvisorAcademicProfileResponse;
import com.eduhub.edu_hub.academic.api.dto.response.GpaGoalResponse;
import com.eduhub.edu_hub.academic.api.dto.response.GpaSimulationSnapshotResponse;
import com.eduhub.edu_hub.academic.api.dto.response.PrerequisiteImpactResponse;
import com.eduhub.edu_hub.academic.api.dto.response.PrerequisiteImpactResponse.*;
import com.eduhub.edu_hub.academic.domain.enums.AcademicRequirementStatus;
import com.eduhub.edu_hub.academic.domain.enums.GpaGoalStatus;
import com.eduhub.edu_hub.academic.domain.enums.PrerequisiteAnalysisScope;
import com.eduhub.edu_hub.academic.domain.enums.PrerequisiteAnalysisStatus;
import com.eduhub.edu_hub.aiassistant.api.dto.request.AdvisorChatRequest;
import com.eduhub.edu_hub.aiassistant.api.dto.response.ChatResponse;
import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.aiassistant.application.port.*;
import com.eduhub.edu_hub.aiassistant.domain.enums.AdvisorRequestState;
import com.eduhub.edu_hub.aiassistant.infrastructure.adapter.AiTokenCounter;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.*;
import com.eduhub.edu_hub.enrollment.api.dto.response.MinimumExamScoreResponse;
import com.eduhub.edu_hub.enrollment.domain.enums.MinimumExamScoreStatus;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.scheduling.api.dto.response.SemesterResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
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
    Semaphore capacity = new Semaphore(settings.getMaximumConcurrentRequests());
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
        when(history.recordExchange(any(), any(), anyString(), anyString(), anyList(), any()))
                .thenAnswer(invocation -> ChatResponse.builder()
                        .sessionId("3")
                        .messageId("11")
                        .content(invocation.getArgument(3))
                        .payload(invocation.getArgument(5))
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
                access, history, model, limiter, settings, ragSettings, counter, clock, executor, scheduler, capacity);
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
                    new BigDecimal("3.50"),
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
                .contains(
                        "GPA tích lũy: chưa đủ dữ liệu",
                        "GPA kỳ hiện tại: 0",
                        "Mục tiêu GPA tốt nghiệp đã lưu: 0",
                        "Mục tiêu GPA kỳ mặc định đã lưu: 3,50");
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

    @Test
    void popupWorkReservesSharedCapacityBeforeChatOpensItsStream() {
        capacity.acquireUninterruptibly(settings.getMaximumConcurrentRequests());
        assertThatThrownBy(() -> service.prepareChat(principal, request()))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.AI_ADVISOR_CAPACITY_EXCEEDED));
        verifyNoInteractions(model, limiter, executor);
    }

    private AdvisorChatRequest request() {
        var request = new AdvisorChatRequest();
        request.setClientRequestId(UUID.randomUUID());
        request.setMessage("Học vụ?");
        return request;
    }

    @Test
    void failedGpaAttemptDoesNotFallBackToAProfileAnswerOrAnEarlierGpaSolution() {
        when(model.answer(any(), anyList())).thenAnswer(invocation -> {
            AdvisorChatExecution execution = invocation.getArgument(0);
            execution.recordAcademicProfile(new AdvisorAcademicProfileResponse(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0,
                    0,
                    false,
                    "Thiếu lịch sử",
                    LocalDateTime.now(clock)));
            execution.beginGpaGoalSearch();
            return new AiAdvisorModelPort.Answer("GPA mới chắc chắn đạt mục tiêu.", List.of());
        });
        var execution = start(request());
        workers.removeFirst().run();
        assertThat(execution.getStatus().response().getContent())
                .contains("Chưa thể xác minh phương án GPA")
                .doesNotContain("GPA tích lũy:", "chắc chắn đạt");
        assertThat(execution.getStatus().response().getPayload().gpaGoal()).isNull();
    }

    private void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(AiAssistantException.class, failure -> assertThat(failure.getErrorCode())
                        .isEqualTo(ErrorCode.AI_CHAT_REQUEST_NOT_FOUND));
    }

    @Test
    void gpaGoalIsRenderedAndSavedInsteadOfBeingReplacedByTheProfileTemplate() {
        when(model.answer(any(), anyList())).thenAnswer(invocation -> {
            AdvisorChatExecution execution = invocation.getArgument(0);
            execution.recordAcademicProfile(new AdvisorAcademicProfileResponse(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0,
                    0,
                    false,
                    "Thiếu lịch sử",
                    LocalDateTime.now(clock)));
            execution.recordGpaGoal(GpaGoalResponse.builder()
                    .status(GpaGoalStatus.INFEASIBLE)
                    .snapshot(GpaSimulationSnapshotResponse.builder()
                            .semesterTargetGpa(new BigDecimal("3.50"))
                            .semester(SemesterResponse.builder()
                                    .id(2L)
                                    .semesterName("Kỳ hiện tại")
                                    .build())
                            .build())
                    .conditions(List.of())
                    .pendingConditions(List.of())
                    .maximumPossibleSemesterGpa(new BigDecimal("3.33"))
                    .calculatedAt(LocalDateTime.now(clock))
                    .message("Không thể đạt với các điều kiện hiện tại.")
                    .build());
            return new AiAdvisorModelPort.Answer("GPA chắc chắn 9.99!", List.of());
        });
        var execution = start(request());
        workers.removeFirst().run();
        assertThat(execution.getStatus().state()).isEqualTo(AdvisorRequestState.COMPLETED);
        assertThat(execution.getStatus().response().getContent())
                .contains("Không thể đạt", "3,50", "3,33")
                .doesNotContain("GPA tích lũy:", "9.99");
        assertThat(execution.getStatus().response().getPayload().gpaGoal()).isNotNull();
        assertThat(events).contains("ui.component", "context.updated");
    }

    @Test
    void aClarificationAfterAProfileReadIsNotHiddenOrDiscardedFromTypedHistory() {
        when(model.answer(any(), anyList())).thenAnswer(invocation -> {
            AdvisorChatExecution execution = invocation.getArgument(0);
            execution.recordAcademicProfile(new AdvisorAcademicProfileResponse(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0,
                    0,
                    false,
                    "Thiếu lịch sử",
                    LocalDateTime.now(clock)));
            return new AiAdvisorModelPort.Answer("Bạn muốn đúng B hay từ B trở lên?", List.of());
        });
        var execution = start(request());
        workers.removeFirst().run();
        assertThat(execution.getStatus().response().getContent()).isEqualTo("Bạn muốn đúng B hay từ B trở lên?");
        assertThat(execution.getStatus().response().getPayload().clarification())
                .isTrue();
    }

    @Test
    void combinedAcademicToolsRenderVerifiedPathsAndExamScoresWithoutModelNumbersOrProfileReplacement() {
        var source = new SubjectReferenceResponse(1L, "GT1", "Giải tích 1");
        var direct = new SubjectReferenceResponse(2L, "GT2", "Giải tích 2");
        var indirect = new SubjectReferenceResponse(3L, "PTVP", "Phương trình vi phân");
        var prerequisites = PrerequisiteImpactResponse.builder()
                .status(PrerequisiteAnalysisStatus.COMPLETED)
                .scope(PrerequisiteAnalysisScope.COURSE_IMPACT)
                .historyComplete(true)
                .graphComplete(true)
                .courseReferences(List.of("GT1"))
                .pendingCourseReferences(List.of())
                .courses(List.of(new CourseImpactResponse(
                        10L,
                        source,
                        true,
                        false,
                        false,
                        AcademicRequirementStatus.SATISFIED,
                        List.of(),
                        true,
                        AcademicRequirementStatus.BLOCKED,
                        List.of(new DependencyImpactResponse(
                                direct, List.of(source, direct), AcademicRequirementStatus.BLOCKED)),
                        List.of(new DependencyImpactResponse(
                                indirect, List.of(source, direct, indirect), AcademicRequirementStatus.BLOCKED)))))
                .calculatedAt(LocalDateTime.now(clock))
                .message("Mỗi môn được xét độc lập.")
                .build();
        var exam = MinimumExamScoreResponse.builder()
                .status(MinimumExamScoreStatus.SOLVED)
                .subjectName("Giải tích 1")
                .componentScore1(new BigDecimal("8"))
                .componentScore2(new BigDecimal("7"))
                .targetGradeLetter("A")
                .processScore(new BigDecimal("7.3"))
                .minimumExamScore(new BigDecimal("8.943"))
                .minimumExamScoreTwoDecimals(new BigDecimal("8.95"))
                .achievedCourseScore10(new BigDecimal("8.5"))
                .achievedGradeLetter("A")
                .calculatedAt(LocalDateTime.now(clock))
                .message("Đã xác minh theo policy Enrollment.")
                .build();
        when(model.answer(any(), anyList())).thenAnswer(invocation -> {
            AdvisorChatExecution execution = invocation.getArgument(0);
            execution.recordPrerequisiteImpact(prerequisites);
            execution.recordExamScore(exam);
            return new AiAdvisorModelPort.Answer("Chỉ cần thi 1.23 và môn này rất dễ.", List.of());
        });

        var execution = start(request());
        workers.removeFirst().run();

        var response = execution.getStatus().response();
        assertThat(response.getContent())
                .contains(
                        "Trực tiếp: GT1 → GT2",
                        "Gián tiếp: GT1 → GT2 → PTVP",
                        "8,943",
                        "8,95",
                        "7,3",
                        "không thay đổi điểm thật")
                .doesNotContain("1.23", "rất dễ", "GPA tích lũy:");
        assertThat(response.getPayload().schemaVersion()).isEqualTo(2);
        assertThat(response.getPayload().prerequisiteImpact()).isSameAs(prerequisites);
        assertThat(response.getPayload().examScore()).isSameAs(exam);
        assertThat(response.getCitations()).isEmpty();
        assertThat(events)
                .containsSubsequence(
                        "ui.component",
                        "context.updated",
                        "ui.component",
                        "context.updated",
                        "text.delta",
                        "agent.completed");
    }

    @Test
    void examClarificationComesFromTheServiceAndIsRetainedWithoutRagCitations() {
        var clarification = MinimumExamScoreResponse.builder()
                .status(MinimumExamScoreStatus.NEEDS_CLARIFICATION)
                .subjectName("Giải tích 1")
                .componentScore1(new BigDecimal("8"))
                .targetGradeLetter("A")
                .message("Bạn cần bổ sung điểm TP2.")
                .calculatedAt(LocalDateTime.now(clock))
                .build();
        when(model.answer(any(), anyList())).thenAnswer(invocation -> {
            AdvisorChatExecution execution = invocation.getArgument(0);
            execution.recordExamScore(clarification);
            return new AiAdvisorModelPort.Answer("Coi TP2 bằng 0.", List.of());
        });

        var execution = start(request());
        workers.removeFirst().run();

        assertThat(execution.getStatus().response().getPayload().clarification())
                .isTrue();
        assertThat(execution.getStatus().response().getPayload().examScore()).isSameAs(clarification);
        assertThat(execution.getStatus().response().getContent())
                .contains("bổ sung điểm TP2")
                .doesNotContain("Coi TP2 bằng 0");
    }

    @Test
    void failedNewToolAttemptsCannotUseAnOldResultOrAModelClaimAsTheCurrentAnswer() {
        when(model.answer(any(), anyList())).thenAnswer(invocation -> {
            AdvisorChatExecution execution = invocation.getArgument(0);
            execution.beginPrerequisiteAnalysis();
            execution.beginExamScoreCalculation();
            return new AiAdvisorModelPort.Answer("Thi 9.99 sẽ đạt và không có môn bị ảnh hưởng.", List.of());
        });

        var execution = start(request());
        workers.removeFirst().run();

        assertThat(execution.getStatus().response().getContent())
                .contains("Chưa thể xác minh phân tích tiên quyết", "Chưa thể xác minh điểm thi tối thiểu")
                .doesNotContain("9.99", "không có môn bị ảnh hưởng");
        assertThat(execution.getStatus().response().getPayload().examScore()).isNull();
        assertThat(execution.getStatus().response().getPayload().prerequisiteImpact())
                .isNull();
    }
}
