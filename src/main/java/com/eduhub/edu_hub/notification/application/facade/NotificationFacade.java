package com.eduhub.edu_hub.notification.application.facade;

import com.eduhub.edu_hub.notification.application.command.QueueAdmissionNotificationCommand;
import com.eduhub.edu_hub.notification.application.command.SendAccountEmailCommand;
import com.eduhub.edu_hub.notification.application.command.SendAdmissionConfirmationEmailCommand;
import com.eduhub.edu_hub.notification.application.facade.dto.AdmissionNotificationInfo;
import com.eduhub.edu_hub.notification.application.result.EmailDeliveryResult;
import java.util.List;

public interface NotificationFacade {

    /** Gửi ngay theo hành vi hiện có; không tự thêm hàng đợi/retry cho luồng đồng bộ. */
    interface ImmediateEmail {
        EmailDeliveryResult sendAccountEmail(SendAccountEmailCommand command);

        EmailDeliveryResult sendAdmissionConfirmationEmail(SendAdmissionConfirmationEmailCommand command);
    }

    /** Hàng đợi nhập học hiện có; chỉ trao đổi ID/DTO, không trả entity của notification. */
    interface AdmissionQueue {
        Long enqueueAdmissionNotification(QueueAdmissionNotificationCommand command);

        List<AdmissionNotificationInfo> findNotificationsByAdmissionIds(List<Long> admissionIds);
    }
}
