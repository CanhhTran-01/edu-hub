package com.eduhub.edu_hub.notification.application.facade.impl;

import com.eduhub.edu_hub.notification.application.command.QueueAdmissionNotificationCommand;
import com.eduhub.edu_hub.notification.application.event.AdmissionNotificationQueued;
import com.eduhub.edu_hub.notification.application.facade.NotificationFacade;
import com.eduhub.edu_hub.notification.application.facade.dto.AdmissionNotificationInfo;
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
    public Long enqueueAdmissionNotification(QueueAdmissionNotificationCommand command) {
        // Tham gia transaction của bên gọi: không lưu hàng đợi hay gửi mail nếu nhập học rollback.
        QueuedAdmissionNotification queuedNotification = createQueuedNotification(command);
        notificationRepository.saveAndFlush(queuedNotification);

        eventPublisher.publishEvent(new AdmissionNotificationQueued(queuedNotification.getId()));
        return queuedNotification.getId();
    }

    private QueuedAdmissionNotification createQueuedNotification(QueueAdmissionNotificationCommand command) {
        return QueuedAdmissionNotification.builder()
                .admissionId(command.admissionId())
                .studentId(command.studentId())
                .recipient(command.recipient())
                .nextAttemptAt(LocalDateTime.now(ZoneOffset.UTC))
                .kind(
                        command.reEnrollment()
                                ? AdmissionNotificationKind.RE_ENROLLMENT
                                : AdmissionNotificationKind.INITIAL_ACCOUNT)
                .state(command.recipient() == null ? NotificationStatus.SKIPPED : NotificationStatus.PENDING)
                .lastError(command.recipient() == null ? "NO_EMAIL" : null)
                .build();
    }

    @Override
    public List<AdmissionNotificationInfo> findNotificationsByAdmissionIds(List<Long> admissionIds) {

        var distinctAdmissionIds = admissionIds.stream().distinct().toList();
        List<AdmissionNotificationInfo> notificationInfos = new ArrayList<>();

        for (int offset = 0; offset < distinctAdmissionIds.size(); offset += QUERY_BATCH_SIZE) {
            notificationRepository
                    .findByAdmissionIdInAndDeletedFalseOrderById(distinctAdmissionIds.subList(
                            offset, Math.min(offset + QUERY_BATCH_SIZE, distinctAdmissionIds.size())))
                    .stream()
                    .map(this::toAdmissionNotificationInfo)
                    .forEach(notificationInfos::add);
        }
        notificationInfos.sort(Comparator.comparing(AdmissionNotificationInfo::id));
        return List.copyOf(notificationInfos);
    }

    private AdmissionNotificationInfo toAdmissionNotificationInfo(QueuedAdmissionNotification notification) {
        return AdmissionNotificationInfo.builder()
                .id(notification.getId())
                .admissionId(notification.getAdmissionId())
                .state(notification.getState().name())
                .failureCount(notification.getFailureCount())
                .error(notification.getLastError())
                .build();
    }
}
