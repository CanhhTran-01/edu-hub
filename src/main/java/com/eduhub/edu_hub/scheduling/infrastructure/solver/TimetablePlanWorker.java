package com.eduhub.edu_hub.scheduling.infrastructure.solver;

import ai.timefold.solver.core.api.solver.SolverManager;
import com.eduhub.edu_hub.scheduling.application.service.TimetablePlanApplicationService;
import com.eduhub.edu_hub.scheduling.application.service.TimetablePlanExecutionService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class TimetablePlanWorker {
    private final SolverManager<TimetableSolution> timetableSolverManager;
    private final TimetablePlanExecutionService executionService;
    private final Executor timetablePlanExecutor;
    private final Map<Long, Long> lastPersistedNanos = new ConcurrentHashMap<>();
    private final Object submissionMonitor = new Object();

    public TimetablePlanWorker(
            SolverManager<TimetableSolution> timetableSolverManager,
            TimetablePlanExecutionService executionService,
            @Qualifier("timetablePlanExecutor") Executor timetablePlanExecutor) {
        this.timetableSolverManager = timetableSolverManager;
        this.executionService = executionService;
        this.timetablePlanExecutor = timetablePlanExecutor;
    }

    @Scheduled(fixedDelayString = "${scheduling.timetabling.recovery-delay-ms:60000}")
    public void recoverExpiredPlans() {
        executionService.expireInterruptedPlans();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void start(TimetablePlanApplicationService.PlanCreated event) {
        // Đưa cả bước dựng mô hình ra khỏi HTTP thread; không được bỏ task âm thầm khi executor từ chối.
        try {
            timetablePlanExecutor.execute(() -> solvePlan(event));
        } catch (RejectedExecutionException failure) {
            recordFailure(event.planId(), failure);
        }
    }

    private void solvePlan(TimetablePlanApplicationService.PlanCreated event) {
        // Cùng monitor với stop để không mất yêu cầu dừng giữa claim DB và submit vào SolverManager.
        synchronized (submissionMonitor) {
            try {
                var problem = executionService.beginSolving(event.planId());
                if (problem == null) return;
                var solution = TimetableSolutionFactory.create(problem);
                if (problem.pinnedModuleClassIds().size() == problem.classes().size()) {
                    executionService.recordBestSolution(event.planId(), solution.toAllocation(), true);
                    return;
                }
                timetableSolverManager
                        .solveBuilder()
                        .withProblemId(event.planId())
                        .withProblem(solution)
                        .withBestSolutionEventConsumer(best -> {
                            long now = System.nanoTime();
                            Long previous = lastPersistedNanos.get(event.planId());
                            if (previous == null || now - previous >= TimeUnit.SECONDS.toNanos(1)) {
                                executionService.recordBestSolution(
                                        event.planId(), best.solution().toAllocation(), false);
                                lastPersistedNanos.put(event.planId(), now);
                            }
                        })
                        .withFinalBestSolutionEventConsumer(best -> {
                            try {
                                executionService.recordBestSolution(
                                        event.planId(), best.solution().toAllocation(), true);
                            } finally {
                                lastPersistedNanos.remove(event.planId());
                            }
                        })
                        .withExceptionHandler((problemId, failure) -> recordFailure(event.planId(), failure))
                        .run();
            } catch (RuntimeException failure) {
                recordFailure(event.planId(), failure);
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void stop(TimetablePlanApplicationService.PlanStopRequested event) {
        synchronized (submissionMonitor) {
            timetableSolverManager.terminateEarly(event.planId());
        }
    }

    private void recordFailure(Long planId, Throwable failure) {
        lastPersistedNanos.remove(planId);
        log.error("Tác vụ xếp lịch thất bại: planId={}", planId, failure);
        executionService.recordFailure(planId);
    }
}
