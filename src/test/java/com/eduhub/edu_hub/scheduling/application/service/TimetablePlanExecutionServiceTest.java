package com.eduhub.edu_hub.scheduling.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.scheduling.TimetableTestData;
import com.eduhub.edu_hub.scheduling.domain.enums.TimetablePlanState;
import com.eduhub.edu_hub.scheduling.domain.model.*;
import com.eduhub.edu_hub.scheduling.domain.repository.TimetablePlanRepository;
import com.eduhub.edu_hub.scheduling.infrastructure.persistence.TimetablePlanCodec;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TimetablePlanExecutionServiceTest {
    private final TimetablePlanRepository repository = mock(TimetablePlanRepository.class);
    private final TimetablePlanCodec codec =
            new TimetablePlanCodec(JsonMapper.builder().build());
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final TimetablePlanExecutionService service = new TimetablePlanExecutionService(repository, codec, clock);
    private final TimetableProblem problem = TimetableTestData.problem(2, 1, 6);

    @Test
    void stopsQueuedWorkWithoutStartingIt() {
        var plan = plan(TimetablePlanState.STOP_REQUESTED);
        assertThat(service.beginSolving(9L)).isNull();
        assertThat(plan.getState()).isEqualTo(TimetablePlanState.STOPPED);
    }

    @Test
    void marksFeasibleOnlyWhenEveryHardConstraintPasses() {
        var plan = plan(TimetablePlanState.RUNNING);
        var allocation = TimetableTestData.allocation(problem);
        service.recordBestSolution(9L, allocation, true);
        assertThat(plan.getState()).isEqualTo(TimetablePlanState.FEASIBLE);
        assertThat(plan.getHardPenalty()).isZero();
        assertThat(codec.read(plan.getAllocationJson(), TimetableAllocation.class))
                .isEqualTo(allocation);
    }

    @Test
    void reportsUnfinishedSearchWithoutClaimingInfeasibility() {
        var plan = plan(TimetablePlanState.RUNNING);
        var allocation = TimetableTestData.allocation(problem);
        var incomplete = new TimetableAllocation(
                allocation.phaseOneStart(),
                allocation.phaseOneEnd(),
                allocation.phaseTwoStart(),
                allocation.subjects(),
                List.of());
        service.recordBestSolution(9L, incomplete, true);
        assertThat(plan.getState()).isEqualTo(TimetablePlanState.NO_FEASIBLE_SOLUTION);
        assertThat(plan.getHardPenalty()).isPositive();
        assertThat(codec.read(plan.getDiagnosticsJson(), String[].class)).isNotEmpty();
    }

    @Test
    void ignoresLateCallbacksAfterPlanWasInterrupted() {
        var plan = plan(TimetablePlanState.INTERRUPTED);
        service.recordBestSolution(9L, TimetableTestData.allocation(problem), true);
        assertThat(plan.getAllocationJson()).isNull();
        assertThat(plan.getState()).isEqualTo(TimetablePlanState.INTERRUPTED);
    }

    @Test
    void keepsValidStoppedDraftAvailableForReview() {
        var plan = plan(TimetablePlanState.STOP_REQUESTED);
        service.recordBestSolution(9L, TimetableTestData.allocation(problem), true);
        assertThat(plan.getState()).isEqualTo(TimetablePlanState.STOPPED);
        assertThat(plan.getHardPenalty()).isZero();
    }

    private TimetablePlan plan(TimetablePlanState state) {
        var plan = TimetablePlan.builder()
                .state(state)
                .semesterId(7L)
                .problemJson(codec.write(problem))
                .diagnosticsJson("[]")
                .build();
        plan.setId(9L);
        when(repository.findForUpdateById(9L)).thenReturn(Optional.of(plan));
        return plan;
    }
}
