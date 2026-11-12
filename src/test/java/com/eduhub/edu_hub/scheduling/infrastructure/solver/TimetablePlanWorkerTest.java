package com.eduhub.edu_hub.scheduling.infrastructure.solver;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ai.timefold.solver.core.api.solver.SolverManager;
import com.eduhub.edu_hub.scheduling.application.service.TimetablePlanApplicationService;
import com.eduhub.edu_hub.scheduling.application.service.TimetablePlanExecutionService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TimetablePlanWorkerTest {
    private final SolverManager<TimetableSolution> solverManager = mock(SolverManager.class);
    private final TimetablePlanExecutionService executionService = mock(TimetablePlanExecutionService.class);

    @Test
    void doesNotConstructOrSolveProblemOnRequestThread() {
        var queuedTask = new AtomicReference<Runnable>();
        var worker = new TimetablePlanWorker(solverManager, executionService, queuedTask::set);
        worker.start(new TimetablePlanApplicationService.PlanCreated(9L));
        assertThat(queuedTask.get()).isNotNull();
        verifyNoInteractions(executionService, solverManager);
        // beginSolving trả null khi yêu cầu dừng đã được xử lý trước khi worker nhận tác vụ.
        queuedTask.get().run();
        verify(executionService).beginSolving(9L);
        verifyNoInteractions(solverManager);
    }

    @Test
    void recordsFailureWhenExecutorRejectsWorkInsteadOfSilentlyDroppingIt() {
        var worker = new TimetablePlanWorker(solverManager, executionService, task -> {
            throw new RejectedExecutionException("Executor đã đóng");
        });
        worker.start(new TimetablePlanApplicationService.PlanCreated(9L));
        verify(executionService).recordFailure(9L);
        verifyNoInteractions(solverManager);
    }

    @Test
    void directsStopToMatchingSolverJob() {
        var worker = new TimetablePlanWorker(solverManager, executionService, Runnable::run);
        worker.stop(new TimetablePlanApplicationService.PlanStopRequested(9L));
        verify(solverManager).terminateEarly(9L);
    }
}
