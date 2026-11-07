package com.eduhub.edu_hub.admission.application.service;

import com.eduhub.edu_hub.admission.application.result.AdmissionResults.NotificationResult;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionNotificationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdmissionNotificationQueryService {
    private final AdmissionNotificationRepository notifications;

    public List<NotificationResult> list(Long planId) {
        return notifications.findByAdmission_Plan_IdOrderById(planId).stream()
                .map(n -> NotificationResult.builder()
                        .id(n.getId())
                        .admissionId(n.getAdmission().getId())
                        .state(n.getState().name())
                        .failureCount(n.getFailureCount())
                        .error(n.getLastError())
                        .build())
                .toList();
    }
}
