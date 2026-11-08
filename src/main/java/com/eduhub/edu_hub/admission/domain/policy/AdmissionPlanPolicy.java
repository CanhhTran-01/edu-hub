package com.eduhub.edu_hub.admission.domain.policy;

import com.eduhub.edu_hub.admission.domain.enums.AdmissionPlanStatus;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.shared.exception.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AdmissionPlanPolicy {

    public static void requireEditable(AdmissionPlan plan) {
        if (plan.getState() == AdmissionPlanStatus.CONFIRMED
                || plan.getState() == AdmissionPlanStatus.CONFIRMING
                || plan.getState() == AdmissionPlanStatus.PARTIALLY_CONFIRMED)
            throw new BaseException(ErrorCode.ADMISSION_INVALID_STATE);
    }

    public static void incrementRevision(AdmissionPlan plan) {
        plan.setRevision(plan.getRevision() + 1);
    }
}
