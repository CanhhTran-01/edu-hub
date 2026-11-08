package com.eduhub.edu_hub.notification.application.facade.impl;

import com.eduhub.edu_hub.notification.api.dto.request.QueueAdmissionNotificationRequest;
import com.eduhub.edu_hub.notification.api.dto.response.AdmissionNotificationResponse;
import com.eduhub.edu_hub.notification.application.event.AdmissionNotificationQueued;
import com.eduhub.edu_hub.notification.application.facade.NotificationFacade;
import com.eduhub.edu_hub.notification.domain.enums.AdmissionNotificationKind;
import com.eduhub.edu_hub.notification.domain.enums.NotificationStatus;
import com.eduhub.edu_hub.notification.domain.model.QueuedAdmissionNotification;
import com.eduhub.edu_hub.notification.domain.repository.QueuedAdmissionNotificationRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdmissionNotificationQueueFacadeImpl implements NotificationFacade.AdmissionQueue {
    private static final int QUERY_BATCH_SIZE = 500;
    private final QueuedAdmissionNotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Long enqueueAdmissionNotification(QueueAdmissionNotificationRequest request) {
        // Tham gia transaction của bên gọi: không lưu hàng đợi hay gửi mail nếu nhập học rollback.
        QueuedAdmissionNotification queuedNotification = createQueuedNotification(request);
        notificationRepository.saveAndFlush(queuedNotification);

        eventPublisher.publishEvent(new AdmissionNotificationQueued(queuedNotification.getId()));
        return queuedNotification.getId();
    }

    private QueuedAdmissionNotification createQueuedNotification(QueueAdmissionNotificationRequest request) {
        return QueuedAdmissionNotification.builder()
                .admissionId(request.admissionId())
                .studentId(request.studentId())
                .recipient(request.recipient())
                .nextAttemptAt(LocalDateTime.now(ZoneOffset.UTC))
                .kind(
                        request.reEnrollment()
                                ? AdmissionNotificationKind.RE_ENROLLMENT
                                : AdmissionNotificationKind.INITIAL_ACCOUNT)
                .state(request.recipient() == null ? NotificationStatus.SKIPPED : NotificationStatus.PENDING)
                .lastError(request.recipient() == null ? "NO_EMAIL" : null)
                .build();
    }

    @Override
    public List<AdmissionNotificationResponse> findNotificationsByAdmissionIds(List<Long> admissionIds) {

        var distinctAdmissionIds = admissionIds.stream().distinct().toList();
        List<AdmissionNotificationResponse> notificationResponses = new ArrayList<>();

        for (int offset = 0; offset < distinctAdmissionIds.size(); offset += QUERY_BATCH_SIZE) {
            notificationRepository
                    .findByAdmissionIdInAndDeletedFalseOrderById(distinctAdmissionIds.subList(
                            offset, Math.min(offset + QUERY_BATCH_SIZE, distinctAdmissionIds.size())))
                    .stream()
                    .map(this::toAdmissionNotificationResponse)
                    .forEach(notificationResponses::add);
        }
        notificationResponses.sort(Comparator.comparing(AdmissionNotificationResponse::id));
        return List.copyOf(notificationResponses);
    }

    private AdmissionNotificationResponse toAdmissionNotificationResponse(QueuedAdmissionNotification notification) {
        return AdmissionNotificationResponse.builder()
                .id(notification.getId())
                .admissionId(notification.getAdmissionId())
                .state(notification.getState().name())
                .failureCount(notification.getFailureCount())
                .error(notification.getLastError())
                .build();
    }
}
