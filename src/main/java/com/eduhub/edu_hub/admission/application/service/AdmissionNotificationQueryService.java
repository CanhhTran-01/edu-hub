package com.eduhub.edu_hub.admission.application.service;

import com.eduhub.edu_hub.admission.application.exception.AdmissionPlanException;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionPlanStatus;
import com.eduhub.edu_hub.admission.domain.model.AdmissionPlan;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionPlanRepository;
import com.eduhub.edu_hub.admission.domain.repository.StudentAdmissionRepository;
import com.eduhub.edu_hub.notification.api.dto.request.SearchAdmissionNotificationsRequest;
import com.eduhub.edu_hub.notification.api.dto.response.AdmissionNotificationDetailResponse;
import com.eduhub.edu_hub.notification.api.dto.response.AdmissionNotificationProgressResponse;
import com.eduhub.edu_hub.notification.api.dto.response.AdmissionNotificationResponse;
import com.eduhub.edu_hub.notification.application.facade.NotificationFacade;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AdmissionNotificationQueryService {
    private final AdmissionPlanRepository planRepository;
    private final StudentAdmissionRepository studentAdmissionRepository;
    private final NotificationFacade.AdmissionQueue notificationQueue;

    public List<AdmissionNotificationResponse> listPlanNotifications(Long planId) {
        requirePlan(planId);
        return notificationQueue.findNotificationsByAdmissionIds(studentAdmissionRepository.findIdsByPlanId(planId));
    }

    public AdmissionNotificationProgressResponse getProgress(Long planId) {
        var plan = requirePlan(planId);
        return notificationQueue.getNotificationProgress(
                planId,
                studentAdmissionRepository.findIdsByPlanId(planId),
                plan.getState() == AdmissionPlanStatus.CONFIRMING,
                plan.getState() == AdmissionPlanStatus.CONFIRMED
                        || plan.getState() == AdmissionPlanStatus.PARTIALLY_CONFIRMED);
    }

    public PageResponse<AdmissionNotificationDetailResponse> searchNotifications(
            Long planId, SearchAdmissionNotificationsRequest request) {
        requirePlan(planId);
        if (request.getPage() < 0 || request.getSize() < 1 || request.getSize() > 100) {
            throw new AdmissionPlanException(ErrorCode.VALIDATION_ERROR);
        }
        return notificationQueue.searchNotifications(studentAdmissionRepository.findIdsByPlanId(planId), request);
    }

    private AdmissionPlan requirePlan(Long planId) {
        // Chỉ kiểm tra plan gốc; không dựng lại toàn bộ hồ sơ/phân lớp cho mỗi lần poll mail.
        return planRepository
                .findByIdAndDeletedFalse(planId)
                .orElseThrow(() -> new AdmissionPlanException(ErrorCode.ADMISSION_PLAN_NOT_FOUND));
    }
}
