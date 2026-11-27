package com.eduhub.edu_hub.aiassistant.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.academic.api.dto.request.GpaGoalRequest;
import com.eduhub.edu_hub.academic.api.dto.request.GpaGoalRequest.CourseGradeConditionRequest;
import com.eduhub.edu_hub.academic.api.dto.response.*;
import com.eduhub.edu_hub.academic.application.exception.GpaSimulationException;
import com.eduhub.edu_hub.academic.application.facade.AcademicFacade;
import com.eduhub.edu_hub.academic.domain.enums.*;
import com.eduhub.edu_hub.aiassistant.infrastructure.adapter.AiTokenCounter;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiAdvisorProperties;
import com.eduhub.edu_hub.enrollment.api.dto.request.MinimumExamScoreRequest;
import com.eduhub.edu_hub.enrollment.api.dto.response.MinimumExamScoreResponse;
import com.eduhub.edu_hub.enrollment.application.facade.EnrollmentFacade;
import com.eduhub.edu_hub.enrollment.domain.enums.MinimumExamScoreStatus;
import com.eduhub.edu_hub.scheduling.api.dto.response.SemesterResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;

class AdvisorToolServiceTest {
    AiAccessService access = mock(AiAccessService.class);
    AcademicFacade.Advisor academic = mock(AcademicFacade.Advisor.class);
    AcademicFacade.GpaSimulation simulation = mock(AcademicFacade.GpaSimulation.class);
    AcademicFacade.Prerequisites prerequisites = mock(AcademicFacade.Prerequisites.class);
    EnrollmentFacade.ExamScore examScores = mock(EnrollmentFacade.ExamScore.class);
    AdvisorToolService service = new AdvisorToolService(
            access, academic, mock(AcademicKnowledgeSearchService.class), simulation, prerequisites, examScores);
    Clock clock = Clock.fixed(Instant.parse("2026-11-27T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    Jwt principal = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("student")
            .build();

    @Test
    void followUpUsesFreshSnapshotAndOnlyInheritsExplicitConditions() {
        var previous = goal(2L);
        var execution = execution(previous);
        when(simulation.findMySnapshot(principal)).thenReturn(snapshot(2L, "b".repeat(64)));
        when(simulation.solveMyGpaGoal(eq(principal), any())).thenReturn(goal(2L));
        var change = new CourseGradeConditionRequest("Y", null, CourseGradeComparison.AT_MOST, "C");
        service.solveGpaGoal(execution, List.of(change), false);
        var request = ArgumentCaptor.forClass(GpaGoalRequest.class);
        verify(simulation).solveMyGpaGoal(eq(principal), request.capture());
        assertThat(request.getValue().snapshotRef()).isEqualTo("b".repeat(64));
        assertThat(request.getValue().existingConditions()).isEqualTo(previous.conditions());
        assertThat(request.getValue().conditionChanges()).containsExactly(change);
        assertThat(execution.getGpaGoal()).isNotNull();
    }

    @Test
    void changedSemesterAsksBeforeCarryingOldEnrollmentConditionsIntoTheNewTerm() {
        var execution = execution(goal(1L));
        when(simulation.findMySnapshot(principal)).thenReturn(snapshot(2L, "b".repeat(64)));
        var response = service.solveGpaGoal(execution, List.of(), false);
        assertThat(response.status()).isEqualTo(GpaGoalStatus.NEEDS_CLARIFICATION);
        assertThat(response.snapshot().semester().getId()).isEqualTo(2L);
        assertThat(response.conditions()).isEmpty();
        verify(simulation, never()).solveMyGpaGoal(any(), any());
    }

    @Test
    void explicitResetStartsFromCurrentSemesterWithoutInheritedConditions() {
        var execution = execution(goal(1L));
        when(simulation.findMySnapshot(principal)).thenReturn(snapshot(2L, "b".repeat(64)));
        when(simulation.solveMyGpaGoal(eq(principal), any())).thenReturn(goal(2L));
        service.solveGpaGoal(execution, List.of(), true);
        var request = ArgumentCaptor.forClass(GpaGoalRequest.class);
        verify(simulation).solveMyGpaGoal(eq(principal), request.capture());
        assertThat(request.getValue().existingConditions()).isEmpty();
        assertThat(request.getValue().conditionChanges()).isEmpty();
    }

    private AdvisorChatExecution execution(GpaGoalResponse previous) {
        return new AdvisorChatExecution(
                UUID.randomUUID(),
                7L,
                principal,
                "Y tối đa C",
                new ChatHistoryService.ConversationContext(3L, 11L, List.of(), previous),
                new AiAdvisorProperties(),
                mock(AiTokenCounter.class),
                clock);
    }

    @Test
    void failedNewAttemptDoesNotExposeThePreviousSuccessfulResultAsTheNewAnswer() {
        var execution = execution(null);
        var earlierGoal = goal(2L);
        execution.recordGpaGoal(earlierGoal);
        when(simulation.findMySnapshot(principal))
                .thenThrow(new GpaSimulationException(ErrorCode.GPA_SIMULATION_SNAPSHOT_CHANGED));
        assertThatThrownBy(() -> service.solveGpaGoal(execution, List.of(), false))
                .isInstanceOf(GpaSimulationException.class);
        assertThat(execution.getGpaGoal()).isNull();
        assertThat(execution.getGpaContext()).isSameAs(earlierGoal);
        assertThat(execution.hasAttemptedGpaGoalSearch()).isTrue();
    }

    private GpaGoalResponse goal(Long semesterId) {
        return GpaGoalResponse.builder()
                .status(GpaGoalStatus.SOLVED)
                .snapshot(snapshot(semesterId, "a".repeat(64)))
                .conditions(List.of(new CourseGradeConditionRequest("X", 1L, CourseGradeComparison.EXACT, "B")))
                .pendingConditions(List.of())
                .calculatedAt(LocalDateTime.now(clock))
                .build();
    }

    @Test
    void examFollowUpKeepsOtherInputsAndAcceptsZeroAsAnExplicitChange() {
        var execution = examExecution();
        when(examScores.solveMinimumExamScore(any())).thenReturn(previousExam());
        service.solveExamScore(execution, null, BigDecimal.ZERO, null, null, false);
        var request = ArgumentCaptor.forClass(MinimumExamScoreRequest.class);
        verify(examScores).solveMinimumExamScore(request.capture());
        assertThat(request.getValue().subjectName()).isEqualTo("Giải tích 1");
        assertThat(request.getValue().componentScore1()).isZero();
        assertThat(request.getValue().componentScore2()).isEqualByComparingTo("7");
        assertThat(request.getValue().targetGradeLetter()).isEqualTo("A");
        verifyNoInteractions(academic, simulation, prerequisites);
    }

    @Test
    void aNewSubjectOrExplicitResetDoesNotReuseAnotherCalculationsScores() {
        when(examScores.solveMinimumExamScore(any())).thenReturn(previousExam());
        service.solveExamScore(examExecution(), "Môn khác", null, null, "B", false);
        service.solveExamScore(examExecution(), "Giải tích 1", null, null, "B", true);
        var request = ArgumentCaptor.forClass(MinimumExamScoreRequest.class);
        verify(examScores, times(2)).solveMinimumExamScore(request.capture());
        assertThat(request.getAllValues()).allSatisfy(inputs -> {
            assertThat(inputs.componentScore1()).isNull();
            assertThat(inputs.componentScore2()).isNull();
        });
    }

    @Test
    void prerequisiteToolReadsFreshDataAndDoesNotModifyGpaAssumptions() {
        var execution = execution(goal(2L));
        var response = PrerequisiteImpactResponse.builder()
                .status(PrerequisiteAnalysisStatus.COMPLETED)
                .scope(PrerequisiteAnalysisScope.COURSE_IMPACT)
                .courses(List.of())
                .courseReferences(List.of("S1"))
                .pendingCourseReferences(List.of())
                .calculatedAt(LocalDateTime.now(clock))
                .build();
        when(prerequisites.analyzeMyPrerequisites(eq(principal), any())).thenReturn(response);
        assertThat(service.analyzePrerequisiteImpact(execution, PrerequisiteAnalysisScope.COURSE_IMPACT, List.of("S1")))
                .isSameAs(response);
        assertThat(execution.getGpaContext().conditions()).hasSize(1);
        assertThat(execution.getPrerequisiteImpact()).isSameAs(response);
        verifyNoInteractions(simulation, examScores);
    }

    @Test
    void cancelledExamCalculationRejectsItsLateResult() {
        var execution = examExecution();
        when(examScores.solveMinimumExamScore(any())).thenAnswer(invocation -> {
            execution.cancel("USER_REQUEST");
            return previousExam();
        });
        assertThatThrownBy(() -> service.solveExamScore(execution, null, null, BigDecimal.TEN, null, false))
                .isInstanceOf(AdvisorChatExecution.AdvisorExecutionStoppedException.class);
        assertThat(execution.getExamScore()).isNull();
    }

    private MinimumExamScoreResponse previousExam() {
        return MinimumExamScoreResponse.builder()
                .status(MinimumExamScoreStatus.SOLVED)
                .subjectName("Giải tích 1")
                .componentScore1(new BigDecimal("8"))
                .componentScore2(new BigDecimal("7"))
                .targetGradeLetter("A")
                .calculatedAt(LocalDateTime.now(clock))
                .build();
    }

    private AdvisorChatExecution examExecution() {
        return new AdvisorChatExecution(
                UUID.randomUUID(),
                7L,
                principal,
                "TP1 là 0 thì sao?",
                new ChatHistoryService.ConversationContext(3L, 11L, List.of(), null, null, previousExam()),
                new AiAdvisorProperties(),
                mock(AiTokenCounter.class),
                clock);
    }

    private GpaSimulationSnapshotResponse snapshot(Long semesterId, String reference) {
        return GpaSimulationSnapshotResponse.builder()
                .snapshotRef(reference)
                .observedAt(LocalDateTime.now(clock))
                .semester(SemesterResponse.builder()
                        .id(semesterId)
                        .semesterName("Kỳ " + semesterId)
                        .build())
                .build();
    }
}
