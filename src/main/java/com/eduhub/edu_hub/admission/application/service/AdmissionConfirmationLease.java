package com.eduhub.edu_hub.admission.application.service;

import com.eduhub.edu_hub.admission.domain.enums.AdmissionPlanStatus;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionPlanRepository;
import com.eduhub.edu_hub.admission.infrastructure.config.AdmissionSettings;
import com.eduhub.edu_hub.shared.exception.*;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdmissionConfirmationLease {
    private final AdmissionPlanRepository planRepository;
    private final AdmissionSettings admissionSettings;

    public AdmissionPlan requireActiveConfirmation(Long planId, String confirmationToken) {
        var plan = planRepository
                .findForUpdate(planId)
                .orElseThrow(() -> new BaseException(ErrorCode.ADMISSION_PLAN_NOT_FOUND));
        requireActiveConfirmation(plan, confirmationToken);
        return plan;
    }

    public void requireActiveConfirmation(AdmissionPlan plan, String confirmationToken) {
        // Đây là mã phối hợp worker, không phải token đăng nhập hay kiểm tra quyền.
        if (confirmationToken == null
                || plan.getState() != AdmissionPlanStatus.CONFIRMING
                || !confirmationToken.equals(plan.getConfirmationToken()))
            throw new BaseException(ErrorCode.ADMISSION_INVALID_STATE);
    }

    public void extendConfirmationLease(AdmissionPlan plan) {
        plan.setProcessingUntil(LocalDateTime.now(ZoneOffset.UTC)
                .plusMinutes(Math.max(1, admissionSettings.getConfirmationLeaseMinutes())));
    }
}
