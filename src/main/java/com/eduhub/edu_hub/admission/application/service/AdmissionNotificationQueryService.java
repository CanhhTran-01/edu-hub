package com.eduhub.edu_hub.admission.application.service;

import com.eduhub.edu_hub.admission.domain.repository.StudentAdmissionRepository;
import com.eduhub.edu_hub.notification.api.dto.response.AdmissionNotificationResponse;
import com.eduhub.edu_hub.notification.application.facade.NotificationFacade;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdmissionNotificationQueryService {
    private final StudentAdmissionRepository studentAdmissionRepository;
    private final NotificationFacade.AdmissionQueue notificationQueue;

    public List<AdmissionNotificationResponse> listPlanNotifications(Long planId) {
        // Admission xác định nguồn thuộc plan
        // notification không đọc entity/repository của admission.
        return notificationQueue.findNotificationsByAdmissionIds(studentAdmissionRepository.findIdsByPlanId(planId));
    }
}
