package com.eduhub.edu_hub.notification.application.facade;

import com.eduhub.edu_hub.notification.application.command.AccountEmailCommand;
import com.eduhub.edu_hub.notification.application.command.AdmissionEmailCommand;
import com.eduhub.edu_hub.notification.application.command.AdmissionNotificationCommand;
import com.eduhub.edu_hub.notification.application.facade.dto.AdmissionNotificationInfo;
import com.eduhub.edu_hub.notification.application.result.EmailDeliveryResult;
import java.util.List;

public interface NotificationFacade {

    /** Gửi ngay theo hành vi hiện có; không tự thêm hàng đợi/retry cho luồng đồng bộ. */
    interface Mail {
        EmailDeliveryResult sendAccount(AccountEmailCommand command);
        EmailDeliveryResult sendAdmissionConfirmation(AdmissionEmailCommand command);
    }

    /** Hàng đợi nhập học hiện có; chỉ trao đổi ID/DTO, không trả entity của notification. */
    interface Queue {
        Long queueAdmission(AdmissionNotificationCommand command);
        List<AdmissionNotificationInfo> findByAdmissionIds(List<Long> admissionIds);
    }
}
