package com.eduhub.edu_hub.admission.infrastructure.notification;

import static com.eduhub.edu_hub.admission.domain.policy.AdmissionNotificationPolicy.MAX_DELIVERY_ATTEMPTS;

import com.eduhub.edu_hub.admission.application.event.AdmissionNotificationQueued;
import com.eduhub.edu_hub.admission.application.service.AdmissionNotificationSender;
import com.eduhub.edu_hub.admission.domain.enums.AdmissionNotificationStatus;
import com.eduhub.edu_hub.admission.domain.repository.AdmissionNotificationRepository;
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
public class AdmissionMailDispatcher {
    // TODO(NOTIFICATION_MODULE): Chuyển listener/scheduler này khi người dùng yêu cầu tách notification.
    // Hiện chỉ điều phối gửi thông báo nhập học qua sender sẵn có, chưa tích hợp notification-service.
    private final AdmissionNotificationRepository notifications;
    private final AdmissionNotificationSender sender;

    @Value("${admission.notifications-enabled:true}")
    private boolean enabled = true;

    @Async("admissionMailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void queued(AdmissionNotificationQueued event) {
        if (enabled) sender.sendInitial(event.notificationId());
    }

    @Scheduled(
            fixedDelayString = "${admission.mail-retry-delay-ms:60000}",
            initialDelayString = "${admission.mail-retry-delay-ms:60000}")
    public void retryDue() {
        if (!enabled) return;
        for (Long id : notifications.findDue(
                List.of(AdmissionNotificationStatus.PENDING, AdmissionNotificationStatus.FAILED),
                LocalDateTime.now(ZoneOffset.UTC),
                MAX_DELIVERY_ATTEMPTS,
                PageRequest.of(0, 100))) sender.send(id);
    }
}
