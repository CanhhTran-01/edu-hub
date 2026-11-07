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
public class AdmissionProcessingLease {
    private final AdmissionPlanRepository plans;
    private final AdmissionSettings settings;

    public AdmissionPlan require(Long id, String token) {
        var plan = plans.findForUpdate(id).orElseThrow(() -> new BaseException(ErrorCode.ADMISSION_PLAN_NOT_FOUND));
        require(plan, token);
        return plan;
    }

    public void require(AdmissionPlan plan, String token) {
        // Đây là mã phối hợp worker, không phải token đăng nhập hay kiểm tra quyền.
        if (token == null
                || plan.getState() != AdmissionPlanStatus.CONFIRMING
                || !token.equals(plan.getConfirmationToken()))
            throw new BaseException(ErrorCode.ADMISSION_INVALID_STATE);
    }

    public void renew(AdmissionPlan plan) {
        plan.setProcessingUntil(
                LocalDateTime.now(ZoneOffset.UTC).plusMinutes(Math.max(1, settings.getConfirmationLeaseMinutes())));
    }
}
