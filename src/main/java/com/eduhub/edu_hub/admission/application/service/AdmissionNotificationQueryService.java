package com.eduhub.edu_hub.admission.application.service;

import com.eduhub.edu_hub.admission.application.result.AdmissionResults.NotificationResult;
import com.eduhub.edu_hub.admission.domain.repository.StudentAdmissionRepository;
import com.eduhub.edu_hub.notification.application.facade.NotificationFacade;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdmissionNotificationQueryService {
    private final StudentAdmissionRepository admissions;
    private final NotificationFacade.Queue notifications;

    public List<NotificationResult> list(Long planId) {
        // Admission xác định nguồn thuộc plan; notification không đọc entity/repository của admission.
        return notifications.findByAdmissionIds(admissions.findIdsByPlanId(planId)).stream()
                .map(n -> NotificationResult.builder()
                        .id(n.id())
                        .admissionId(n.admissionId())
                        .state(n.state())
                        .failureCount(n.failureCount())
                        .error(n.error())
                        .build())
                .toList();
    }
}
