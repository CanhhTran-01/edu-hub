package com.eduhub.edu_hub.scheduling.application.service;

import com.eduhub.edu_hub.scheduling.domain.enums.TimetablePlanState;
import com.eduhub.edu_hub.scheduling.domain.model.*;
import com.eduhub.edu_hub.scheduling.domain.policy.TimetablePolicy;
import com.eduhub.edu_hub.scheduling.domain.repository.TimetablePlanRepository;
import com.eduhub.edu_hub.scheduling.infrastructure.persistence.TimetablePlanCodec;
import java.time.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Các transaction ngắn của worker; không giữ khóa trong thời gian bộ giải tìm kiếm.
 * REQUIRES_NEW ở callback bảo đảm ghi được sau AFTER_COMMIT, không dùng lại transaction vừa kết thúc.
 */
@Service
@RequiredArgsConstructor
public class TimetablePlanExecutionService {
    public static final List<TimetablePlanState> ACTIVE_STATES =
            List.of(TimetablePlanState.QUEUED, TimetablePlanState.RUNNING, TimetablePlanState.STOP_REQUESTED);
    private final TimetablePlanRepository planRepository;
    private final TimetablePlanCodec codec;
    private final Clock clock;

    // TODO(PLAN_CLEANUP): Chưa có job xóa plan hết hạn (expiresAt); cân nhắc giữ APPLIED, giới hạn số plan/học kỳ.

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TimetableProblem beginSolving(Long planId) {
        var plan = planRepository.findForUpdateById(planId).orElseThrow();
        if (plan.getState() == TimetablePlanState.STOP_REQUESTED) {
            plan.setState(TimetablePlanState.STOPPED);
            plan.setUpdatedAt(now());
            return null;
        }
        if (plan.getState() != TimetablePlanState.QUEUED) return null;
        plan.setState(TimetablePlanState.RUNNING);
        plan.setUpdatedAt(now());
        return codec.read(plan.getProblemJson(), TimetableProblem.class);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBestSolution(Long planId, TimetableAllocation allocation, boolean finished) {
        var plan = planRepository.findForUpdateById(planId).orElseThrow();
        if (!plan.getState().isActive()) return;
        var problem = codec.read(plan.getProblemJson(), TimetableProblem.class);
        var assessment = TimetablePolicy.assess(problem, allocation, true);
        if (plan.getHardPenalty() == null
                || assessment.hardPenalty() < plan.getHardPenalty()
                || (assessment.hardPenalty() == plan.getHardPenalty()
                        && assessment.softPenalty() <= plan.getSoftPenalty())) {
            plan.setAllocationJson(codec.write(allocation));
            plan.setHardPenalty(assessment.hardPenalty());
            plan.setSoftPenalty(assessment.softPenalty());
            plan.setDiagnosticsJson(codec.write(assessment.violations()));
        }
        if (finished)
            plan.setState(
                    plan.getState() == TimetablePlanState.STOP_REQUESTED
                            ? TimetablePlanState.STOPPED
                            : Long.valueOf(0).equals(plan.getHardPenalty())
                                    ? TimetablePlanState.FEASIBLE
                                    : TimetablePlanState.NO_FEASIBLE_SOLUTION);
        plan.setUpdatedAt(now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long planId) {
        planRepository
                .findForUpdateById(planId)
                .filter(plan -> plan.getState().isActive())
                .ifPresent(plan -> {
                    plan.setState(TimetablePlanState.FAILED);
                    plan.setDiagnosticsJson(
                            codec.write(List.of("Tác vụ xếp lịch bị lỗi; lịch chính thức được giữ nguyên")));
                    plan.setUpdatedAt(now());
                });
    }

    @Transactional
    public void expireInterruptedPlans() {
        for (var plan : planRepository.findExpiredForUpdate(ACTIVE_STATES, now())) {
            plan.setState(TimetablePlanState.INTERRUPTED);
            plan.setDiagnosticsJson(codec.write(List.of("Tác vụ không hoàn tất trước hạn; hãy tạo phương án mới")));
            plan.setUpdatedAt(now());
        }
        planRepository.flush();
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
