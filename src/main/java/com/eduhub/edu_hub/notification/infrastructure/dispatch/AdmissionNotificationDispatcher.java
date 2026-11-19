package com.eduhub.edu_hub.notification.infrastructure.dispatch;

import com.eduhub.edu_hub.notification.application.event.AdmissionNotificationQueued;
import com.eduhub.edu_hub.notification.application.service.AdmissionNotificationDispatchService;
import com.eduhub.edu_hub.notification.application.service.QueuedAdmissionNotificationSender;
import com.eduhub.edu_hub.shared.systemconfig.application.service.SystemConfigService;
import com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey;
import lombok.RequiredArgsConstructor;
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

    private final SystemConfigService systemConfigService;

    @Async("notificationMailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAdmissionNotificationQueued(AdmissionNotificationQueued event) {
        if (systemConfigService.getSnapshot().getBoolean(SystemConfigKey.ADMISSION_NOTIFICATIONS_ENABLED))
            notificationSender.sendInitialAttempt(event.notificationId());
    }

    @Scheduled(
            fixedDelayString = "${notification.mail-retry-delay-ms:${admission.mail-retry-delay-ms:60000}}",
            initialDelayString = "${notification.mail-retry-delay-ms:${admission.mail-retry-delay-ms:60000}}")
    public void sendDueNotifications() {

        if (!systemConfigService.getSnapshot().getBoolean(SystemConfigKey.ADMISSION_NOTIFICATIONS_ENABLED))
            return; // Công tắc bật/tắt job (có thể cấu hình)

        dispatchService.sendDueNotifications();
    }
}
