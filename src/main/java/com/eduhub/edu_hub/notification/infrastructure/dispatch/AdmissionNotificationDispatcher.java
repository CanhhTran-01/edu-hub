package com.eduhub.edu_hub.notification.infrastructure.dispatch;

import com.eduhub.edu_hub.notification.application.event.AdmissionNotificationQueued;
import com.eduhub.edu_hub.notification.application.service.AdmissionNotificationDispatchService;
import com.eduhub.edu_hub.notification.application.service.QueuedAdmissionNotificationSender;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
@RequiredArgsConstructor
public class AdmissionNotificationDispatcher {
    // TODO(NOTIFICATION_DELIVERY): Khi tích hợp dịch vụ ngoài, bổ sung idempotency và phục hồi trạng thái.

    private final AdmissionNotificationDispatchService dispatchService;
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

        dispatchService.sendDueNotifications();
    }
}
