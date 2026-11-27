package com.eduhub.edu_hub.aiassistant.application.service;

import com.eduhub.edu_hub.academic.api.dto.request.GpaGoalRequest;
import com.eduhub.edu_hub.academic.api.dto.request.GpaGoalRequest.CourseGradeConditionRequest;
import com.eduhub.edu_hub.academic.api.dto.request.PrerequisiteImpactRequest;
import com.eduhub.edu_hub.academic.api.dto.response.AdvisorAcademicProfileResponse;
import com.eduhub.edu_hub.academic.api.dto.response.GpaGoalResponse;
import com.eduhub.edu_hub.academic.api.dto.response.PrerequisiteImpactResponse;
import com.eduhub.edu_hub.academic.application.facade.AcademicFacade;
import com.eduhub.edu_hub.academic.domain.enums.GpaGoalStatus;
import com.eduhub.edu_hub.academic.domain.enums.PrerequisiteAnalysisScope;
import com.eduhub.edu_hub.aiassistant.domain.policy.AiContentPolicy;
import com.eduhub.edu_hub.enrollment.api.dto.request.MinimumExamScoreRequest;
import com.eduhub.edu_hub.enrollment.api.dto.response.MinimumExamScoreResponse;
import com.eduhub.edu_hub.enrollment.application.facade.EnrollmentFacade;
import com.eduhub.edu_hub.shared.common.TextsNormalizer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Capability đọc/tính dùng principal trong execution phía server, không nhận danh tính từ arguments của model. */
@Service
@RequiredArgsConstructor
public class AdvisorToolService {
    private final AiAccessService accessService;
    private final AcademicFacade.Advisor academicFacade;
    private final AcademicKnowledgeSearchService knowledgeSearchService;
    private final AcademicFacade.GpaSimulation gpaSimulationFacade;
    private final AcademicFacade.Prerequisites prerequisiteFacade;
    private final EnrollmentFacade.ExamScore examScoreFacade;

    public AdvisorAcademicProfileResponse getAcademicProfile(AdvisorChatExecution execution) {
        accessService.requireStudent(execution.getPrincipal());
        var profile = academicFacade.findMyAcademicProfile(execution.getPrincipal());
        execution.recordAcademicProfile(profile);
        return profile;
    }

    public PrerequisiteImpactResponse analyzePrerequisiteImpact(
            AdvisorChatExecution execution, PrerequisiteAnalysisScope scope, List<String> courseReferences) {
        accessService.requireStudent(execution.getPrincipal());
        execution.beginPrerequisiteAnalysis();
        var response = prerequisiteFacade.analyzeMyPrerequisites(
                execution.getPrincipal(), new PrerequisiteImpactRequest(scope, courseReferences));
        execution.recordPrerequisiteImpact(response);
        return response;
    }

    public MinimumExamScoreResponse solveExamScore(
            AdvisorChatExecution execution,
            String subjectName,
            BigDecimal componentScore1,
            BigDecimal componentScore2,
            String targetGradeLetter,
            boolean resetInputs) {
        accessService.requireStudent(execution.getPrincipal());
        var previous = execution.getExamScoreContext();
        execution.beginExamScoreCalculation();
        // Chỉ kế thừa số đã nhập của cùng môn; đổi môn phải cung cấp lại đủ đầu vào.
        boolean canInherit = !resetInputs
                && previous != null
                && (subjectName == null
                        || TextsNormalizer.normalizeKey(subjectName)
                                .equals(TextsNormalizer.normalizeKey(previous.subjectName())));
        var request = new MinimumExamScoreRequest(
                subjectName != null ? subjectName : canInherit ? previous.subjectName() : null,
                componentScore1 != null ? componentScore1 : canInherit ? previous.componentScore1() : null,
                componentScore2 != null ? componentScore2 : canInherit ? previous.componentScore2() : null,
                targetGradeLetter != null ? targetGradeLetter : canInherit ? previous.targetGradeLetter() : null);
        var response = examScoreFacade.solveMinimumExamScore(request);
        execution.recordExamScore(response);
        return response;
    }

    public GpaGoalResponse solveGpaGoal(
            AdvisorChatExecution execution,
            List<CourseGradeConditionRequest> conditionChanges,
            boolean resetConditions) {
        accessService.requireStudent(execution.getPrincipal());
        execution.checkCanContinue();
        var previousGoal = execution.getGpaContext();
        execution.beginGpaGoalSearch();
        var snapshot = gpaSimulationFacade.findMySnapshot(execution.getPrincipal());
        GpaGoalResponse goal;
        if (!resetConditions
                && previousGoal != null
                && !snapshot.semester()
                        .getId()
                        .equals(previousGoal.snapshot().semester().getId())) {
            goal = GpaGoalResponse.builder()
                    .status(GpaGoalStatus.NEEDS_CLARIFICATION)
                    .snapshot(snapshot)
                    .conditions(List.of())
                    .pendingConditions(conditionChanges)
                    .calculatedAt(snapshot.observedAt())
                    .message("Kết quả trước thuộc kỳ "
                            + previousGoal.snapshot().semester().getSemesterName()
                            + "; phép tính mới chỉ chạy ở kỳ "
                            + snapshot.semester().getSemesterName()
                            + ". Bạn muốn bắt đầu phương án cho kỳ hiện tại với các điều kiện nào?")
                    .build();
        } else {
            var existingConditions = resetConditions || previousGoal == null
                    ? List.<CourseGradeConditionRequest>of()
                    : previousGoal.conditions();
            // Khi chỉ yêu cầu tính lại sau cập nhật Profile, giữ cả điều kiện đang chờ của lượt trước.
            var effectiveChanges = !resetConditions && previousGoal != null && conditionChanges.isEmpty()
                    ? previousGoal.pendingConditions()
                    : conditionChanges;
            goal = gpaSimulationFacade.solveMyGpaGoal(
                    execution.getPrincipal(),
                    new GpaGoalRequest(snapshot.snapshotRef(), existingConditions, effectiveChanges));
        }
        execution.recordGpaGoal(goal);
        return goal;
    }

    public List<KnowledgePassage> searchAcademicKnowledge(AdvisorChatExecution execution, String query) {
        accessService.requireStudent(execution.getPrincipal());
        var chunks = knowledgeSearchService.findRelevantKnowledge(query);
        execution.recordKnowledgeChunks(chunks);
        return chunks.stream()
                .map(chunk -> new KnowledgePassage(
                        "K" + chunk.chunkId(),
                        chunk.chunkId(),
                        chunk.sourceTitle(),
                        chunk.pageNumber(),
                        chunk.effectiveDate(),
                        AiContentPolicy.redactPromptText(chunk.content())))
                .toList();
    }

    public record KnowledgePassage(
            String referenceCode,
            Long chunkId,
            String sourceTitle,
            Integer pageNumber,
            LocalDate effectiveDate,
            String content) {}
}
