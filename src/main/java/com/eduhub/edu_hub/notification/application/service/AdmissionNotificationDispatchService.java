package com.eduhub.edu_hub.notification.application.service;

import static com.eduhub.edu_hub.notification.domain.policy.NotificationRetryPolicy.MAX_DELIVERY_ATTEMPTS;

import com.eduhub.edu_hub.notification.domain.enums.NotificationStatus;
import com.eduhub.edu_hub.notification.domain.repository.QueuedAdmissionNotificationRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** Tải thông báo đến hạn và gọi sender để mỗi lần gửi dùng transaction riêng. */
@Service
@RequiredArgsConstructor
public class AdmissionNotificationDispatchService {
    private static final int DISPATCH_BATCH_SIZE = 100;

    private final QueuedAdmissionNotificationRepository notificationRepository;
    private final QueuedAdmissionNotificationSender notificationSender;

    public void sendDueNotifications() {
        // Không mở transaction bao quanh cả lô; sender vẫn đi qua Spring proxy với REQUIRES_NEW.
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
