package com.eduhub.edu_hub.scheduling.infrastructure.solver;

import static org.assertj.core.api.Assertions.*;

import ai.timefold.solver.core.api.solver.SolverFactory;
import com.eduhub.edu_hub.scheduling.TimetableTestData;
import com.eduhub.edu_hub.scheduling.domain.model.TimetableProblem;
import com.eduhub.edu_hub.scheduling.domain.policy.TimetablePolicy;
import com.eduhub.edu_hub.scheduling.infrastructure.config.TimetablingConfiguration;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TimetableSolverTest {
    @Test
    void solvesSmallProblemWithoutSpringOrDatabase() {
        var problem = TimetableTestData.problem(2, 2, 9);
        var solver = SolverFactory.<TimetableSolution>create(
                        TimetablingConfiguration.solverConfig(Duration.ofMillis(300)))
                .buildSolver();
        var solution = solver.solve(TimetableSolutionFactory.create(problem));
        var assessment = TimetablePolicy.assess(problem, solution.toAllocation(), true);
        assertThat(assessment.violations()).isEmpty();
        assertThat(solution.getScore().isFeasible()).isTrue();
        assertThat(solution.toAllocation().sessions()).hasSize(12);
    }

    @Test
    void repairsResourceConflictsInsteadOfRelyingOnInitialAssignment() {
        var problem = TimetableTestData.problem(1, 2, 6);
        var initial = TimetableSolutionFactory.create(problem);
        initial.getClasses()
                .get(1)
                .setLecturerId(initial.getClasses().getFirst().getLecturerId());
        initial.getSessions().get(2).setSlot(initial.getSessions().getFirst().getSlot());
        initial.getSessions().get(2).setRoomId(initial.getSessions().getFirst().getRoomId());
        assertThat(TimetablePolicy.assess(problem, initial.toAllocation(), true).isFeasible())
                .isFalse();
        var solution = SolverFactory.<TimetableSolution>create(
                        TimetablingConfiguration.solverConfig(Duration.ofMillis(500)))
                .buildSolver()
                .solve(initial);
        assertThat(TimetablePolicy.assess(problem, solution.toAllocation(), true)
                        .violations())
                .isEmpty();
    }

    @Test
    void schedulesRepresentativeSixteenClassesWithAllTeachingSessions() {
        var problem = TimetableTestData.problem(8, 2, 45);
        var solution = SolverFactory.<TimetableSolution>create(
                        TimetablingConfiguration.solverConfig(Duration.ofSeconds(1)))
                .buildSolver()
                .solve(TimetableSolutionFactory.create(problem));
        assertThat(TimetablePolicy.assess(problem, solution.toAllocation(), true)
                        .violations())
                .isEmpty();
        assertThat(solution.toAllocation().sessions()).hasSize(240);
    }

    @Test
    void preservesPinnedClassAndItsPhaseBoundaries() {
        var originalProblem = TimetableTestData.problem(2, 1, 9);
        var original = TimetableTestData.allocation(originalProblem);
        var problem = new TimetableProblem(
                originalProblem.semesterId(),
                originalProblem.startDate(),
                originalProblem.endDate(),
                originalProblem.registrationStart(),
                originalProblem.registrationEnd(),
                originalProblem.classes(),
                originalProblem.lecturers(),
                originalProblem.rooms(),
                originalProblem.existingSessions(),
                originalProblem.replacedSessions(),
                Set.of(100L),
                original);
        var solution = SolverFactory.<TimetableSolution>create(
                        TimetablingConfiguration.solverConfig(Duration.ofMillis(300)))
                .buildSolver()
                .solve(TimetableSolutionFactory.create(problem));
        var allocation = solution.toAllocation();
        assertThat(TimetablePolicy.assess(problem, allocation, true).violations())
                .isEmpty();
        assertThat(allocation.sessions().stream()
                        .filter(session -> session.moduleClassId().equals(100L))
                        .toList())
                .containsExactlyElementsOf(original.sessions().stream()
                        .filter(session -> session.moduleClassId().equals(100L))
                        .toList());
        assertThat(allocation.phaseTwoStart()).isEqualTo(original.phaseTwoStart());
    }
}
