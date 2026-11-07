package com.eduhub.edu_hub.admission.domain.policy;

/** One initial delivery plus three scheduled retries. Never reset the persisted failure count. */
public final class AdmissionNotificationPolicy {
    public static final int MAX_RETRIES = 3;
    public static final int MAX_DELIVERY_ATTEMPTS = 1 + MAX_RETRIES;

    private AdmissionNotificationPolicy() {}
}
