package com.eduhub.edu_hub.notification.domain.enums;

/** Trạng thái hiển thị tiến trình; không bổ sung giá trị vào enum/cột state đang lưu trong database. */
public enum NotificationDeliveryState {
    PENDING,
    WAITING_RETRY,
    SENT,
    PERMANENTLY_FAILED,
    SKIPPED
}
