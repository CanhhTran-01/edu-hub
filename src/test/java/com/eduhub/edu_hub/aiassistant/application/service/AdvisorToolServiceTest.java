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
import com.eduhub.edu_hub.scheduling.api.dto.response.SemesterResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;

class AdvisorToolServiceTest {
    AiAccessService access = mock(AiAccessService.class);
    AcademicFacade.Advisor academic = mock(AcademicFacade.Advisor.class);
    AcademicFacade.GpaSimulation simulation = mock(AcademicFacade.GpaSimulation.class);
    AdvisorToolService service =
            new AdvisorToolService(access, academic, mock(AcademicKnowledgeSearchService.class), simulation);
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
