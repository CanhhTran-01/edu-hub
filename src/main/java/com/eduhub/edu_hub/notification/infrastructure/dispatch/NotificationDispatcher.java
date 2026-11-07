package com.eduhub.edu_hub.notification.infrastructure.dispatch;

import static com.eduhub.edu_hub.notification.domain.policy.NotificationRetryPolicy.MAX_DELIVERY_ATTEMPTS;

import com.eduhub.edu_hub.notification.application.event.NotificationQueued;
import com.eduhub.edu_hub.notification.application.service.QueuedNotificationSender;
import com.eduhub.edu_hub.notification.domain.enums.NotificationStatus;
import com.eduhub.edu_hub.notification.domain.repository.QueuedNotificationRepository;
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
public class NotificationDispatcher {

    // TODO(NOTIFICATION_DELIVERY): Khi tích hợp dịch vụ ngoài, bổ sung idempotency và phục hồi trạng thái.

    private final QueuedNotificationRepository notifications;
    private final QueuedNotificationSender sender;
    // Hàng đợi hiện phục vụ nhập học. Luồng thêm đơn lẻ/reset mật khẩu giữ cách gửi đồng bộ hiện có.

    @Value("${notification.admission-enabled:${admission.notifications-enabled:true}}")
    private boolean enabled = true;

    @Async("notificationMailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void queued(NotificationQueued event) {
        if (enabled) sender.sendInitial(event.notificationId());
    }

    @Scheduled(
            fixedDelayString = "${notification.mail-retry-delay-ms:${admission.mail-retry-delay-ms:60000}}",
            initialDelayString = "${notification.mail-retry-delay-ms:${admission.mail-retry-delay-ms:60000}}")
    public void retryDue() {

        if (!enabled) return; // Công tắc bật/tắt job (có thể cấu hình)

        for (Long id : notifications.findDue(
                List.of(NotificationStatus.PENDING, NotificationStatus.FAILED),
                LocalDateTime.now(ZoneOffset.UTC), MAX_DELIVERY_ATTEMPTS,
                PageRequest.of(0, 100))) {

            sender.send(id); //  lấy các thông báo đến hạn gửi, gọi sender từng cái
        }
    }
}
