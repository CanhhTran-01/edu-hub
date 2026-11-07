package com.eduhub.edu_hub.people.application.admission;

import com.eduhub.edu_hub.people.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.people.domain.repository.AdmissionPlanRepository;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;

public final class AdmissionWorkflowSupport {
    private AdmissionWorkflowSupport() {}

    public static AdmissionPlan locked(AdmissionPlanRepository plans, Long id, long revision) {
        // findForUpdate: khóa row, chống việc hai người bấm cùng lúc làm dữ liệu lệch
        AdmissionPlan plan =
                plans.findForUpdate(id).orElseThrow(() -> new BaseException(ErrorCode.ADMISSION_PLAN_NOT_FOUND));

        // optimistic locking thủ công, số phiên bản không khớp thì người dùng tải lại trước khi sửa tiếp
        if (plan.getRevision() != revision) throw new BaseException(ErrorCode.ADMISSION_STALE_PLAN);

        return plan;
    }

    public static void requireEditable(AdmissionPlan plan) {
        // chặn sửa khi đã chốt
        if (plan.getState() == AdmissionPlan.State.CONFIRMED
                || plan.getState() == AdmissionPlan.State.CONFIRMING
                || plan.getState() == AdmissionPlan.State.PARTIALLY_CONFIRMED) {
            throw new BaseException(ErrorCode.ADMISSION_INVALID_STATE);
        }
    }

    public static void changed(AdmissionPlan plan) {
        // sửa thành công thì tăng revision lên một phiên bản
        plan.setRevision(plan.getRevision() + 1);
    }
}
