package com.eduhub.edu_hub.aiassistant.application.service;

import com.eduhub.edu_hub.academic.api.dto.request.GpaSimulationRequest;
import com.eduhub.edu_hub.academic.api.dto.response.GpaSimulationPreviewResponse;
import com.eduhub.edu_hub.academic.api.dto.response.GpaSimulationSnapshotResponse;
import com.eduhub.edu_hub.academic.application.facade.AcademicFacade;
import com.eduhub.edu_hub.aiassistant.api.dto.response.GpaSimulationCommentResponse;
import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.aiassistant.application.port.AiAdvisorModelPort;
import com.eduhub.edu_hub.aiassistant.infrastructure.adapter.AiTokenCounter;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiAdvisorProperties;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Use-case popup độc lập chat: không mở transaction qua model và không ghi hội thoại. */
@Service
public class AiGpaSimulationService {
    private static final int INPUT_TOKEN_MARGIN = 512;
    private final AiAccessService accessService;
    private final AcademicFacade.GpaSimulation academicFacade;
    private final AiAdvisorModelPort modelPort;
    private final AiQuestionRateLimiter rateLimiter;
    private final AiTokenCounter tokenCounter;
    private final AiAdvisorProperties properties;
    private final ObjectMapper objectMapper;
    private final Executor executor;
    private final Semaphore capacity;

    private record CommentContext(
            BigDecimal semesterGpa,
            BigDecimal cumulativeGpa,
            BigDecimal semesterTargetGpa,
            Boolean meetsSemesterTarget,
            boolean historyComplete,
            List<CourseCommentContext> courses) {}

    private record CourseCommentContext(
            String subjectName, int credits, boolean hasPublishedGrade, BigDecimal grade4) {}

    public AiGpaSimulationService(
            AiAccessService accessService,
            AcademicFacade.GpaSimulation academicFacade,
            AiAdvisorModelPort modelPort,
            AiQuestionRateLimiter rateLimiter,
            AiTokenCounter tokenCounter,
            AiAdvisorProperties properties,
            ObjectMapper objectMapper,
            @Qualifier("aiAdvisorExecutor") Executor executor,
            @Qualifier("aiAdvisorCapacity") Semaphore capacity) {
        this.accessService = accessService;
        this.academicFacade = academicFacade;
        this.modelPort = modelPort;
        this.rateLimiter = rateLimiter;
        this.tokenCounter = tokenCounter;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.capacity = capacity;
    }

    public GpaSimulationSnapshotResponse findMySnapshot(Jwt principal) {
        accessService.requireStudent(principal);
        return academicFacade.findMySnapshot(principal);
    }

    public GpaSimulationPreviewResponse previewMyGpa(Jwt principal, GpaSimulationRequest request) {
        accessService.requireStudent(principal);
        return academicFacade.previewMyGpa(principal, request);
    }

    public GpaSimulationCommentResponse analyzeMySimulation(Jwt principal, GpaSimulationRequest request) {
        Long userId = accessService.requireStudent(principal).getId();
        long deadline = System.nanoTime() + properties.getExecutionTimeout().toNanos();
        // Xác minh và kết thúc snapshot transaction trước khi chi quota hoặc gọi model.
        var preview = academicFacade.previewMyGpa(principal, request);
        String prompt = properties.requireSimulationCommentPrompt();
        String verifiedJson = objectMapper.writeValueAsString(new CommentContext(
                preview.semesterGpa(),
                preview.cumulativeGpa(),
                preview.semesterTargetGpa(),
                preview.meetsSemesterTarget(),
                preview.historyComplete(),
                preview.courses().stream()
                        .map(course -> new CourseCommentContext(
                                course.subjectName(), course.credits(), course.hasPublishedGrade(), course.grade4()))
                        .toList()));
        long inputTokens = (long) tokenCounter.countTokens(prompt + verifiedJson) + INPUT_TOKEN_MARGIN;
        if (inputTokens > properties.getMaximumContextTokens()
                || inputTokens + properties.getMaximumOutputTokens() > properties.getMaximumTotalTokens()
                || System.nanoTime() >= deadline) throw unavailable();
        if (!capacity.tryAcquire()) throw new AiAssistantException(ErrorCode.AI_ADVISOR_CAPACITY_EXCEEDED);
        var task = new FutureTask<>(() -> {
            if (System.nanoTime() >= deadline) throw unavailable();
            rateLimiter.consumeQuestionAllowance(userId);
            return modelPort.commentOnSimulation(verifiedJson);
        });
        try {
            // Dùng chung pool hữu hạn, không có hàng chờ với chat. Worker vẫn chiếm suất nếu SDK chưa thoát sau hủy.
            executor.execute(() -> {
                try {
                    task.run();
                } finally {
                    capacity.release();
                }
            });
        } catch (RejectedExecutionException exception) {
            capacity.release();
            throw new AiAssistantException(ErrorCode.AI_ADVISOR_CAPACITY_EXCEEDED);
        }
        try {
            String comment = task.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            return new GpaSimulationCommentResponse(preview, renderVerifiedSummary(preview) + comment);
        } catch (TimeoutException exception) {
            task.cancel(true);
            throw unavailable();
        } catch (InterruptedException exception) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof AiAssistantException advisorException) throw advisorException;
            throw unavailable();
        }
    }

    private String renderVerifiedSummary(GpaSimulationPreviewResponse preview) {
        var summary = new StringBuilder();
        if (preview.semesterGpa() == null) summary.append("Kỳ được chọn chưa có tín chỉ để tính GPA. ");
        else
            summary.append("GPA học kỳ trong giả định là ")
                    .append(preview.semesterGpa().toPlainString())
                    .append(". ");
        if (preview.cumulativeGpa() != null)
            summary.append("GPA tích lũy đến cuối kỳ là ")
                    .append(preview.cumulativeGpa().toPlainString())
                    .append(". ");
        else if (!preview.historyComplete()) summary.append("Chưa đủ lịch sử để kết luận GPA tích lũy. ");
        if (preview.meetsSemesterTarget() != null) {
            summary.append(
                            Boolean.TRUE.equals(preview.meetsSemesterTarget())
                                    ? "Giả định đạt mục tiêu kỳ "
                                    : "Giả định chưa đạt mục tiêu kỳ ")
                    .append(preview.semesterTargetGpa().toPlainString())
                    .append(". ");
        } else if (preview.semesterTargetGpa() == null)
            summary.append("Bạn chưa đặt mục tiêu GPA kỳ; hãy cập nhật trong hồ sơ cá nhân. ");
        return summary.toString();
    }

    private AiAssistantException unavailable() {
        return new AiAssistantException(ErrorCode.AI_SERVICE_UNAVAILABLE);
    }
}
