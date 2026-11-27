package com.eduhub.edu_hub.aiassistant.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.academic.api.dto.request.GpaSimulationRequest;
import com.eduhub.edu_hub.academic.api.dto.response.GpaSimulationPreviewResponse;
import com.eduhub.edu_hub.academic.application.exception.GpaSimulationException;
import com.eduhub.edu_hub.academic.application.facade.AcademicFacade;
import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.aiassistant.application.port.AiAdvisorModelPort;
import com.eduhub.edu_hub.aiassistant.infrastructure.adapter.AiTokenCounter;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiAdvisorProperties;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.json.JsonMapper;

class AiGpaSimulationServiceTest {
    AiAccessService access = mock(AiAccessService.class);
    AcademicFacade.GpaSimulation academic = mock(AcademicFacade.GpaSimulation.class);
    AiAdvisorModelPort model = mock(AiAdvisorModelPort.class);
    AiQuestionRateLimiter rateLimiter = mock(AiQuestionRateLimiter.class);
    AiTokenCounter tokens = mock(AiTokenCounter.class);
    AiAdvisorProperties properties = new AiAdvisorProperties();
    Semaphore capacity = new Semaphore(properties.getMaximumConcurrentRequests());
    Jwt principal = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("student")
            .build();
    GpaSimulationRequest request = new GpaSimulationRequest("a".repeat(64), List.of());
    AiGpaSimulationService service = createService(Runnable::run);

    @BeforeEach
    void setup() {
        properties.setSimulationCommentPrompt("Chỉ nhận xét định tính từ dữ liệu đã kiểm chứng.");
        when(access.requireStudent(principal))
                .thenReturn(UserInfoResponse.builder().id(7L).build());
        when(academic.previewMyGpa(principal, request)).thenReturn(preview(true));
        when(model.commentOnSimulation(anyString())).thenReturn("Bạn có thể cân nhắc lại các môn còn được điều chỉnh.");
    }

    @Test
    void snapshotAndPreviewNeverCallModelOrConsumeQuestionQuota() {
        service.findMySnapshot(principal);
        service.previewMyGpa(principal, request);
        verifyNoInteractions(model, rateLimiter, tokens);
        verify(academic).findMySnapshot(principal);
        verify(academic).previewMyGpa(principal, request);
    }

    @Test
    void analysisRevalidatesBeforeModelAndReturnsVerifiedNumbersWithoutChatHistory() {
        int initialPermits = capacity.availablePermits();
        var response = service.analyzeMySimulation(principal, request);
        assertThat(capacity.availablePermits()).isEqualTo(initialPermits);
        assertThat(response.preview().semesterGpa()).isEqualByComparingTo("3.25");
        assertThat(response.comment()).contains("3.25", "3.10", "Bạn có thể cân nhắc");
        assertThat(response.comment()).doesNotContain("GPA kỳ 9.99");
        var input = ArgumentCaptor.forClass(String.class);
        verify(model).commentOnSimulation(input.capture());
        assertThat(input.getValue())
                .contains("Học phần mẫu")
                .doesNotContain("999", "enrollmentId", "semesterId", "snapshotRef");
        verify(model, never()).answer(any(), anyList());
        verify(rateLimiter).consumeQuestionAllowance(7L);
        var order = inOrder(academic, rateLimiter, model);
        order.verify(academic).previewMyGpa(principal, request);
        order.verify(rateLimiter).consumeQuestionAllowance(7L);
        order.verify(model).commentOnSimulation(anyString());
    }

    @Test
    void staleSnapshotDoesNotSpendModelQuota() {
        when(academic.previewMyGpa(principal, request))
                .thenThrow(new GpaSimulationException(ErrorCode.GPA_SIMULATION_SNAPSHOT_CHANGED));
        assertThatThrownBy(() -> service.analyzeMySimulation(principal, request))
                .isInstanceOf(GpaSimulationException.class);
        verifyNoInteractions(model, rateLimiter, tokens);
    }

    @Test
    void accessFailureStopsBothDataReadsAndModelCalls() {
        when(access.requireStudent(principal)).thenThrow(new AiAssistantException(ErrorCode.UNAUTHORIZED));
        assertThatThrownBy(() -> service.analyzeMySimulation(principal, request))
                .isInstanceOf(AiAssistantException.class);
        verifyNoInteractions(academic, model, rateLimiter);
    }

    @Test
    void exhaustedRateLimitIsPreservedWithoutCallingTheModel() {
        doThrow(new AiAssistantException(ErrorCode.AI_RATE_LIMIT_EXCEEDED))
                .when(rateLimiter)
                .consumeQuestionAllowance(7L);
        assertThatThrownBy(() -> service.analyzeMySimulation(principal, request))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.AI_RATE_LIMIT_EXCEEDED));
        verifyNoInteractions(model);
    }

    @Test
    void rejectedSharedWorkerDoesNotSpendQuotaOrRunModel() {
        int initialPermits = capacity.availablePermits();
        var busyService = createService(command -> {
            throw new RejectedExecutionException();
        });
        assertThatThrownBy(() -> busyService.analyzeMySimulation(principal, request))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.AI_ADVISOR_CAPACITY_EXCEEDED));
        verifyNoInteractions(model, rateLimiter);
        assertThat(capacity.availablePermits()).isEqualTo(initialPermits);
    }

    @Test
    void exhaustedSharedCapacityRejectsAnalysisBeforeSpendingQuota() {
        capacity.drainPermits();
        assertThatThrownBy(() -> service.analyzeMySimulation(principal, request))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.AI_ADVISOR_CAPACITY_EXCEEDED));
        verifyNoInteractions(model, rateLimiter);
    }

    @Test
    void tokenBudgetIsCheckedBeforeSubmittingModelWork() {
        when(tokens.countTokens(anyString())).thenReturn(properties.getMaximumContextTokens());
        assertThatThrownBy(() -> service.analyzeMySimulation(principal, request))
                .isInstanceOf(AiAssistantException.class);
        verifyNoInteractions(model, rateLimiter);
    }

    @Test
    void queuedTaskThatMissesItsDeadlineNeverReturnsAComment() {
        properties.setExecutionTimeout(Duration.ofSeconds(1));
        var submittedTask = new AtomicReference<Runnable>();
        int initialPermits = capacity.availablePermits();
        var waitingService = createService(submittedTask::set);
        assertThatThrownBy(() -> waitingService.analyzeMySimulation(principal, request))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE));
        assertThat(capacity.availablePermits()).isEqualTo(initialPermits - 1);
        submittedTask.get().run();
        assertThat(capacity.availablePermits()).isEqualTo(initialPermits);
        verifyNoInteractions(model, rateLimiter);
    }

    @Test
    void incompleteHistoryDoesNotRenderUnknownCumulativeGpaAsZero() {
        when(academic.previewMyGpa(principal, request)).thenReturn(preview(false));
        var response = service.analyzeMySimulation(principal, request);
        assertThat(response.comment()).contains("Chưa đủ lịch sử", "chưa đặt mục tiêu GPA kỳ", "hồ sơ cá nhân");
        assertThat(response.comment()).doesNotContain("tích lũy đến cuối kỳ là 0");
    }

    private AiGpaSimulationService createService(Executor executor) {
        return new AiGpaSimulationService(
                access,
                academic,
                model,
                rateLimiter,
                tokens,
                properties,
                JsonMapper.builder().findAndAddModules().build(),
                executor,
                capacity);
    }

    private GpaSimulationPreviewResponse preview(boolean complete) {
        return GpaSimulationPreviewResponse.builder()
                .snapshotRef("a".repeat(64))
                .semesterId(2L)
                .semesterGpa(new BigDecimal("3.25"))
                .cumulativeGpa(complete ? new BigDecimal("3.10") : null)
                .historyComplete(complete)
                .semesterCredits(3)
                .courses(List.of(new GpaSimulationPreviewResponse.CourseResponse(
                        999L, "PRIVATE_CODE", "Học phần mẫu", 3, false, new BigDecimal("3"), List.of("B"))))
                .build();
    }
}
