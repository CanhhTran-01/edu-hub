package com.eduhub.edu_hub.notification.application.facade.impl;

import com.eduhub.edu_hub.notification.application.command.AdmissionNotificationCommand;
import com.eduhub.edu_hub.notification.application.event.NotificationQueued;
import com.eduhub.edu_hub.notification.application.facade.NotificationFacade;
import com.eduhub.edu_hub.notification.application.facade.dto.AdmissionNotificationInfo;
import com.eduhub.edu_hub.notification.domain.enums.NotificationKind;
import com.eduhub.edu_hub.notification.domain.enums.NotificationStatus;
import com.eduhub.edu_hub.notification.domain.model.QueuedNotification;
import com.eduhub.edu_hub.notification.domain.repository.QueuedNotificationRepository;
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
public class QueueNotificationFacadeImpl implements NotificationFacade.Queue {
    private static final int QUERY_BATCH_SIZE = 500;
    private final QueuedNotificationRepository notifications;
    private final ApplicationEventPublisher events;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Long queueAdmission(AdmissionNotificationCommand command) {
        // Tham gia transaction của bên gọi: không lưu hàng đợi hay gửi mail nếu nhập học rollback.
        QueuedNotification notice = QueuedNotification.builder()
                .admissionId(command.admissionId())
                .studentId(command.studentId())
                .recipient(command.recipient())
                .nextAttemptAt(LocalDateTime.now(ZoneOffset.UTC))
                .kind(command.reEnrollment() ? NotificationKind.RE_ENROLLMENT : NotificationKind.INITIAL_ACCOUNT)
                .state(command.recipient() == null ? NotificationStatus.SKIPPED : NotificationStatus.PENDING)
                .lastError(command.recipient() == null ? "NO_EMAIL" : null)
                .build();
        notifications.saveAndFlush(notice);
        events.publishEvent(new NotificationQueued(notice.getId()));
        return notice.getId();
    }

    @Override
    public List<AdmissionNotificationInfo> findByAdmissionIds(List<Long> admissionIds) {
        var ids = admissionIds.stream().distinct().toList();
        List<AdmissionNotificationInfo> result = new ArrayList<>();
        for (int offset = 0; offset < ids.size(); offset += QUERY_BATCH_SIZE) {
            notifications
                    .findByAdmissionIdInAndDeletedFalseOrderById(
                            ids.subList(offset, Math.min(offset + QUERY_BATCH_SIZE, ids.size())))
                    .forEach(n -> result.add(AdmissionNotificationInfo.builder()
                            .id(n.getId())
                            .admissionId(n.getAdmissionId())
                            .state(n.getState().name())
                            .failureCount(n.getFailureCount())
                            .error(n.getLastError())
                            .build()));
        }
        result.sort(Comparator.comparing(AdmissionNotificationInfo::id));
        return List.copyOf(result);
    }
}
