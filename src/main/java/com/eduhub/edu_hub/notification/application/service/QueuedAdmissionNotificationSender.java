package com.eduhub.edu_hub.notification.application.service;

import static com.eduhub.edu_hub.notification.domain.policy.NotificationRetryPolicy.MAX_DELIVERY_ATTEMPTS;
import static com.eduhub.edu_hub.notification.domain.policy.NotificationRetryPolicy.RETRY_DELAY_MINUTES;

import com.eduhub.edu_hub.identity.api.dto.response.AccountNotificationResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.notification.domain.enums.AdmissionNotificationKind;
import com.eduhub.edu_hub.notification.domain.enums.NotificationStatus;
import com.eduhub.edu_hub.notification.domain.model.QueuedAdmissionNotification;
import com.eduhub.edu_hub.notification.domain.policy.NotificationRetryPolicy;
import com.eduhub.edu_hub.notification.domain.repository.QueuedAdmissionNotificationRepository;
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
public class QueuedAdmissionNotificationSender {
    private static final String INITIAL_ACCOUNT_PASSWORD = "12345";
    private static final int MAX_ERROR_CODE_LENGTH = 255;

    private final QueuedAdmissionNotificationRepository notificationRepository;
    private final IdentityFacade identityFacade;
    private final PeopleFacade.Admission peopleAdmissionFacade;

    // IdentityFacade vẫn xác thực tài khoản/mật khẩu ở mỗi lần gửi.

    // TODO(NOTIFICATION_SERVICE): Thống nhất hợp đồng kiểm tra này trước khi chuyển gửi sang dịch vụ ngoài.

    /** Chỉ post-commit async kickoff mới gọi phương thức này. Delayed kickoff không thể thực hiện retry. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendInitialAttempt(Long notificationId) {

        QueuedAdmissionNotification notification =
                notificationRepository.findByIdForUpdate(notificationId).orElse(null);
        LocalDateTime attemptTime = LocalDateTime.now(ZoneOffset.UTC);

        if (!NotificationRetryPolicy.allowsInitialAttempt(notification, attemptTime)) return;

        deliverAndRecordOutcome(notification, attemptTime);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendScheduledAttempt(Long notificationId) {

        QueuedAdmissionNotification notification =
                notificationRepository.findByIdForUpdate(notificationId).orElse(null);
        LocalDateTime attemptTime = LocalDateTime.now(ZoneOffset.UTC);

        if (!NotificationRetryPolicy.allowsScheduledAttempt(notification, attemptTime)) return;

        deliverAndRecordOutcome(notification, attemptTime);
    }

    private void deliverAndRecordOutcome(QueuedAdmissionNotification notification, LocalDateTime attemptTime) {
        try {
            AccountNotificationResponse deliveryResult = sendUsingCurrentStudentAccount(notification);

            // cập nhật trạng thái
            recordDeliveryResult(notification, deliveryResult, attemptTime);
        } catch (RuntimeException exception) {
            log.warn(
                    "Notification delivery crashed: notificationId={}, failureType={}",
                    notification.getId(),
                    exception.getClass().getSimpleName());
            recordFailedAttempt(notification, "EMAIL_SEND_FAILED", attemptTime);
        }

        // Đồng bộ sang module học sinh
        synchronizeStudentNotificationStatus(notification);
        logDeliveryOutcome(notification);
    }

    private AccountNotificationResponse sendUsingCurrentStudentAccount(QueuedAdmissionNotification notification) {
        // lấy sinh viên
        var student = peopleAdmissionFacade
                .findStudentsByIds(java.util.Set.of(notification.getStudentId()))
                .getFirst();

        // chọn loại thông báo và gửi đi
        return notification.getKind() == AdmissionNotificationKind.INITIAL_ACCOUNT
                ? identityFacade.sendStudentAccountNotification(student.getUserId(), INITIAL_ACCOUNT_PASSWORD)
                : identityFacade.sendStudentAdmissionNotification(student.getUserId(), notification.getRecipient());
    }

    private void recordDeliveryResult(
            QueuedAdmissionNotification notification,
            AccountNotificationResponse deliveryResult,
            LocalDateTime attemptTime) {
        if (deliveryResult.emailSent()) {
            notification.setState(NotificationStatus.SENT);
            notification.setLastError(null);
        } else {
            recordFailedAttempt(notification, deliveryResult.emailError(), attemptTime);
        }
    }

    private void recordFailedAttempt(
            QueuedAdmissionNotification notification, String errorCode, LocalDateTime attemptTime) {
        notification.setState(NotificationStatus.FAILED);
        notification.setFailureCount(notification.getFailureCount() + 1);
        notification.setLastError(
                errorCode == null
                        ? "EMAIL_SEND_FAILED"
                        : errorCode.substring(0, Math.min(MAX_ERROR_CODE_LENGTH, errorCode.length())));
        notification.setNextAttemptAt(attemptTime.plusMinutes(RETRY_DELAY_MINUTES));
    }

    private void synchronizeStudentNotificationStatus(QueuedAdmissionNotification notification) {
        peopleAdmissionFacade.updateNotificationStatus(
                notification.getStudentId(),
                AccountNotificationStatus.valueOf(notification.getState().name()),
                notification.getLastError());
    }

    private void logDeliveryOutcome(QueuedAdmissionNotification notification) {
        // TODO(AUDIT_LOG): Lưu lần gửi đầu, từng retry do scheduler, lỗi và kết quả hết lượt sau commit.
        //  Giữ một lần đầu + ba retry rồi chấm dứt; hiện tại không có thao tác thủ công/reset bộ đếm.

        if (notification.getState() == NotificationStatus.FAILED
                && notification.getFailureCount() == MAX_DELIVERY_ATTEMPTS) {
            log.warn(
                    "Admission email permanently failed after 3 retries: notificationId={}, error={}",
                    notification.getId(),
                    notification.getLastError());
        } else {
            log.info(
                    "Admission notification: notificationId={}, state={}, failures={}",
                    notification.getId(),
                    notification.getState(),
                    notification.getFailureCount());
        }
    }
}
