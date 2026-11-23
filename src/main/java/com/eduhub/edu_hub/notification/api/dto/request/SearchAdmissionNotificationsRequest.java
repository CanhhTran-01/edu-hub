package com.eduhub.edu_hub.notification.api.dto.request;

import com.eduhub.edu_hub.notification.domain.enums.NotificationDeliveryState;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/** Bộ lọc chỉ áp dụng trong tập admissionId của plan do Admission cung cấp. */
@Getter
@Setter
public class SearchAdmissionNotificationsRequest {
    private NotificationDeliveryState deliveryState;

    @Min(0)
    private int page = 0;

    @Min(1)
    @Max(100)
    private int size = 20;
}
