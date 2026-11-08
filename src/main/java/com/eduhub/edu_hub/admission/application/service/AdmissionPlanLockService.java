package com.eduhub.edu_hub.admission.application.service;

import com.eduhub.edu_hub.admission.application.exception.AdmissionPlanException;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionPlanRepository;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class AdmissionPlanLockService {
    private final AdmissionPlanRepository planRepository;

    public AdmissionPlan lockPlanAtRevision(Long planId, long expectedRevision) {
        // findForUpdate: khóa row, chống việc hai người bấm cùng lúc làm dữ liệu lệch
        AdmissionPlan plan = planRepository
                .findForUpdate(planId)
                .orElseThrow(() -> new AdmissionPlanException(ErrorCode.ADMISSION_PLAN_NOT_FOUND));

        // optimistic locking thủ công, số phiên bản không khớp thì người dùng tải lại trước khi sửa tiếp
        if (plan.getRevision() != expectedRevision) throw new AdmissionPlanException(ErrorCode.ADMISSION_STALE_PLAN);

        return plan;
    }
}
