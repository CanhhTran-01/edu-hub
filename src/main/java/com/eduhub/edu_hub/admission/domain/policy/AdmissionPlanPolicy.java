package com.eduhub.edu_hub.admission.domain.policy;

import com.eduhub.edu_hub.admission.domain.enums.AdmissionPlanStatus;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.shared.exception.*;

public final class AdmissionPlanPolicy {
    private AdmissionPlanPolicy() {}

    public static void requireEditable(AdmissionPlan plan) {
        if (plan.getState() == AdmissionPlanStatus.CONFIRMED
                || plan.getState() == AdmissionPlanStatus.CONFIRMING
                || plan.getState() == AdmissionPlanStatus.PARTIALLY_CONFIRMED)
            throw new BaseException(ErrorCode.ADMISSION_INVALID_STATE);
    }

    public static void changed(AdmissionPlan plan) {
        plan.setRevision(plan.getRevision() + 1);
    }
}
