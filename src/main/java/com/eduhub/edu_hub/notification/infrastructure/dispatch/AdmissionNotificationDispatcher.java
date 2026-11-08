package com.eduhub.edu_hub.notification.infrastructure.dispatch;

import static com.eduhub.edu_hub.notification.domain.policy.NotificationRetryPolicy.MAX_DELIVERY_ATTEMPTS;

import com.eduhub.edu_hub.notification.application.event.AdmissionNotificationQueued;
import com.eduhub.edu_hub.notification.application.service.QueuedAdmissionNotificationSender;
import com.eduhub.edu_hub.notification.domain.enums.NotificationStatus;
import com.eduhub.edu_hub.notification.domain.repository.QueuedAdmissionNotificationRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
@RequiredArgsConstructor
public class AdmissionNotificationDispatcher {
    private static final int DISPATCH_BATCH_SIZE = 100;

    // TODO(NOTIFICATION_DELIVERY): Khi tích hợp dịch vụ ngoài, bổ sung idempotency và phục hồi trạng thái.

    private final QueuedAdmissionNotificationRepository notificationRepository;
    private final QueuedAdmissionNotificationSender notificationSender;
    // Hàng đợi hiện phục vụ nhập học. Luồng thêm đơn lẻ/reset mật khẩu giữ cách gửi đồng bộ hiện có.

    @Value("${notification.admission-enabled:${admission.notifications-enabled:true}}")
    private boolean admissionNotificationsEnabled = true;

    @Async("notificationMailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAdmissionNotificationQueued(AdmissionNotificationQueued event) {
        if (admissionNotificationsEnabled) notificationSender.sendInitialAttempt(event.notificationId());
    }

    @Scheduled(
            fixedDelayString = "${notification.mail-retry-delay-ms:${admission.mail-retry-delay-ms:60000}}",
            initialDelayString = "${notification.mail-retry-delay-ms:${admission.mail-retry-delay-ms:60000}}")
    public void sendDueNotifications() {

        if (!admissionNotificationsEnabled) return; // Công tắc bật/tắt job (có thể cấu hình)

        for (Long notificationId : notificationRepository.findDueNotificationIds(
                List.of(NotificationStatus.PENDING, NotificationStatus.FAILED),
                LocalDateTime.now(ZoneOffset.UTC),
                MAX_DELIVERY_ATTEMPTS,
                PageRequest.of(0, DISPATCH_BATCH_SIZE))) {

            notificationSender.sendScheduledAttempt(
                    notificationId); //  lấy các thông báo đến hạn gửi, gọi sender từng cái
        }
    }
}
