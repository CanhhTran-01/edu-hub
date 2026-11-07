package com.eduhub.edu_hub.notification.domain.policy;

/** One initial delivery plus three scheduled retries. Never reset the persisted failure count. */
public final class NotificationRetryPolicy {
    public static final int MAX_RETRIES = 3;
    public static final int MAX_DELIVERY_ATTEMPTS = 1 + MAX_RETRIES;

    private NotificationRetryPolicy() {}
}
