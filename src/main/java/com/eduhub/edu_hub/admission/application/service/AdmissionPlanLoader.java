package com.eduhub.edu_hub.admission.application.service;

import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionPlanRepository;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;

public final class AdmissionPlanLoader {
    private AdmissionPlanLoader() {}

    public static AdmissionPlan locked(AdmissionPlanRepository plans, Long id, long revision) {
        // findForUpdate: khóa row, chống việc hai người bấm cùng lúc làm dữ liệu lệch
        AdmissionPlan plan =
                plans.findForUpdate(id).orElseThrow(() -> new BaseException(ErrorCode.ADMISSION_PLAN_NOT_FOUND));

        // optimistic locking thủ công, số phiên bản không khớp thì người dùng tải lại trước khi sửa tiếp
        if (plan.getRevision() != revision) throw new BaseException(ErrorCode.ADMISSION_STALE_PLAN);

        return plan;
    }
}
