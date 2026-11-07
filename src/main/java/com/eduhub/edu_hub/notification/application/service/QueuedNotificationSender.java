package com.eduhub.edu_hub.notification.application.service;

import static com.eduhub.edu_hub.notification.domain.policy.NotificationRetryPolicy.MAX_DELIVERY_ATTEMPTS;

import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.notification.domain.enums.NotificationKind;
import com.eduhub.edu_hub.notification.domain.enums.NotificationStatus;
import com.eduhub.edu_hub.notification.domain.model.QueuedNotification;
import com.eduhub.edu_hub.notification.domain.repository.QueuedNotificationRepository;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.domain.enums.AccountNotificationStatus;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueuedNotificationSender {
    private final QueuedNotificationRepository notifications;
    private final IdentityFacade identity;
    private final PeopleFacade.Admission people;

    // IdentityFacade vẫn xác thực tài khoản/mật khẩu ở mỗi lần gửi.

    // TODO(NOTIFICATION_SERVICE): Thống nhất hợp đồng kiểm tra này trước khi chuyển gửi sang dịch vụ ngoài.

    /** Chỉ post-commit async kickoff mới gọi phương thức này. Delayed kickoff không thể thực hiện retry. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendInitial(Long id) {

        QueuedNotification row = notifications.findForUpdate(id).orElse(null);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        if (row == null || row.getState() != NotificationStatus.PENDING
                || row.getFailureCount() != 0 || row.getNextAttemptAt().isAfter(now)) {
            return;
        }

        deliver(row, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void send(Long id) {

        QueuedNotification row = notifications.findForUpdate(id).orElse(null);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        if (row == null
                || row.getState() == NotificationStatus.SENT
                || row.getState() == NotificationStatus.SKIPPED
                || row.getFailureCount() >= MAX_DELIVERY_ATTEMPTS
                || row.getNextAttemptAt().isAfter(now)) return;

        deliver(row, now);
    }

    private void deliver(QueuedNotification row, LocalDateTime now) {

        try {
            var student = people.findStudentsByIds(java.util.Set.of(row.getStudentId())).getFirst();

            var result = row.getKind() == NotificationKind.INITIAL_ACCOUNT
                    ? identity.sendStudentAccountNotification(student.getUserId(), "12345")
                    : identity.sendStudentAdmissionNotification(student.getUserId(), row.getRecipient());

            if (result.emailSent()) {
                row.setState(NotificationStatus.SENT);
                row.setLastError(null);
            } else {
                failed(row, result.emailError(), now);
            }

        } catch (RuntimeException exception) {
            failed(row, "EMAIL_SEND_FAILED", now);
        }

        people.updateNotificationStatus(
                row.getStudentId(),
                AccountNotificationStatus.valueOf(row.getState().name()),
                row.getLastError());

        // TODO(AUDIT_LOG): Lưu lần gửi đầu, từng retry do scheduler, lỗi và kết quả hết lượt sau commit.
        //  Giữ một lần đầu + ba retry rồi chấm dứt; hiện tại không có thao tác thủ công/reset bộ đếm.

        if (row.getState() == NotificationStatus.FAILED && row.getFailureCount() == MAX_DELIVERY_ATTEMPTS) {
            log.warn("Admission email permanently failed after 3 retries: notificationId={}, error={}",
                    row.getId(), row.getLastError());
        } else {
            log.info("Admission notification: notificationId={}, state={}, failures={}",
                    row.getId(), row.getState(), row.getFailureCount());
        }
    }

    private void failed(QueuedNotification row, String error, LocalDateTime now) {
        row.setState(NotificationStatus.FAILED);
        row.setFailureCount(row.getFailureCount() + 1);
        row.setLastError(error == null ? "EMAIL_SEND_FAILED" : error.substring(0, Math.min(255, error.length())));
        row.setNextAttemptAt(now.plusMinutes(1));
    }
}
