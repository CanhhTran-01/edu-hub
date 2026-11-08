package com.eduhub.edu_hub.notification.application.facade;

import com.eduhub.edu_hub.notification.api.dto.request.QueueAdmissionNotificationRequest;
import com.eduhub.edu_hub.notification.api.dto.request.SendAccountEmailRequest;
import com.eduhub.edu_hub.notification.api.dto.request.SendAdmissionConfirmationEmailRequest;
import com.eduhub.edu_hub.notification.api.dto.response.AdmissionNotificationResponse;
import com.eduhub.edu_hub.notification.api.dto.response.EmailDeliveryResponse;
import java.util.List;

public interface NotificationFacade {

    /** Gửi ngay theo hành vi hiện có; không tự thêm hàng đợi/retry cho luồng đồng bộ. */
    interface ImmediateEmail {
        EmailDeliveryResponse sendAccountEmail(SendAccountEmailRequest request);

        EmailDeliveryResponse sendAdmissionConfirmationEmail(SendAdmissionConfirmationEmailRequest request);
    }

    /** Hàng đợi nhập học hiện có; chỉ trao đổi ID/DTO, không trả entity của notification. */
    interface AdmissionQueue {
        Long enqueueAdmissionNotification(QueueAdmissionNotificationRequest request);

        List<AdmissionNotificationResponse> findNotificationsByAdmissionIds(List<Long> admissionIds);
    }
}
