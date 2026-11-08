package com.eduhub.edu_hub.notification.domain.policy;

import com.eduhub.edu_hub.notification.domain.enums.NotificationStatus;
import com.eduhub.edu_hub.notification.domain.model.QueuedAdmissionNotification;
import java.time.LocalDateTime;

/** One initial delivery plus three scheduled retries. Never reset the persisted failure count. */
public final class NotificationRetryPolicy {
    public static final int MAX_RETRIES = 3;
    public static final int MAX_DELIVERY_ATTEMPTS = 1 + MAX_RETRIES;
    public static final int RETRY_DELAY_MINUTES = 1;

    private NotificationRetryPolicy() {}

    public static boolean allowsInitialAttempt(QueuedAdmissionNotification notification, LocalDateTime attemptTime) {
        return notification != null
                && notification.getState() == NotificationStatus.PENDING
                && notification.getFailureCount() == 0
                && !notification.getNextAttemptAt().isAfter(attemptTime);
    }

    public static boolean allowsScheduledAttempt(QueuedAdmissionNotification notification, LocalDateTime attemptTime) {
        return notification != null
                && notification.getState() != NotificationStatus.SENT
                && notification.getState() != NotificationStatus.SKIPPED
                && notification.getFailureCount() < MAX_DELIVERY_ATTEMPTS
                && !notification.getNextAttemptAt().isAfter(attemptTime);
    }
}
