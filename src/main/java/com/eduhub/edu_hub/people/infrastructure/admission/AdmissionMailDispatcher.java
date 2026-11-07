package com.eduhub.edu_hub.people.infrastructure.admission;

import static com.eduhub.edu_hub.people.domain.policy.AdmissionNotificationPolicy.MAX_DELIVERY_ATTEMPTS;

import com.eduhub.edu_hub.people.application.admission.AdmissionNotificationSender;
import com.eduhub.edu_hub.people.application.admission.AdmissionQueued;
import com.eduhub.edu_hub.people.domain.model.AdmissionNotification;
import com.eduhub.edu_hub.people.domain.repository.AdmissionNotificationRepository;
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
    private final AdmissionNotificationRepository notifications;
    private final AdmissionNotificationSender sender;

    @Value("${admission.notifications-enabled:true}")
    private boolean enabled = true;

    @Async("admissionMailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void queued(AdmissionQueued event) {
        if (enabled) sender.sendInitial(event.notificationId());
    }

    @Scheduled(
            fixedDelayString = "${admission.mail-retry-delay-ms:60000}",
            initialDelayString = "${admission.mail-retry-delay-ms:60000}")
    public void retryDue() {
        if (!enabled) return;
        for (Long id : notifications.findDue(
                List.of(AdmissionNotification.State.PENDING, AdmissionNotification.State.FAILED),
                LocalDateTime.now(ZoneOffset.UTC),
                MAX_DELIVERY_ATTEMPTS,
                PageRequest.of(0, 100))) sender.send(id);
    }
}
