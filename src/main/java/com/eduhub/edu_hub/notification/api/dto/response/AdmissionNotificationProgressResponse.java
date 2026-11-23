package com.eduhub.edu_hub.notification.api.dto.response;

import java.math.BigDecimal;

/** Hoàn tất xử lý gồm SENT, SKIPPED và lỗi hết lượt; 100% không đồng nghĩa tất cả mail thành công. */
public record AdmissionNotificationProgressResponse(
        Long planId,
        long totalNotifications,
        long sentCount,
        long pendingCount,
        long retryWaitingCount,
        long permanentlyFailedCount,
        long skippedCount,
        long completedCount,
        BigDecimal completionPercentage,
        boolean isComplete,
        boolean dispatchEnabled) {}
