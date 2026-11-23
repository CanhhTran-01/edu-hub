package com.eduhub.edu_hub.notification.api.dto.response;

import com.eduhub.edu_hub.notification.domain.enums.NotificationDeliveryState;
import java.time.LocalDateTime;

/** updatedAt là metadata UTC; nextAttemptAt là giờ Việt Nam và chỉ có khi còn chờ gửi/thử lại. */
public record AdmissionNotificationDetailResponse(
        Long id,
        Long admissionId,
        Long studentId,
        String fullName,
        String studentCode,
        NotificationDeliveryState deliveryState,
        int failureCount,
        LocalDateTime nextAttemptAt,
        String error,
        LocalDateTime updatedAt) {}
