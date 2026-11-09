package com.eduhub.edu_hub.people.application.service;

import com.eduhub.edu_hub.identity.api.dto.response.AccountNotificationResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.response.LecturerNotificationResponse;
import com.eduhub.edu_hub.people.application.exception.LecturerAccountNotificationException;
import com.eduhub.edu_hub.people.domain.enums.AccountNotificationStatus;
import com.eduhub.edu_hub.people.domain.repository.LecturerRepository;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LecturerAccountNotificationService {
    private final LecturerRepository lecturerRepository;
    private final IdentityFacade identityFacade;

    public LecturerNotificationResponse sendAccountNotification(Long lecturerId) {

        var lecturer = lecturerRepository
                .findByIdAndDeletedFalse(lecturerId)
                .orElseThrow(() -> new LecturerAccountNotificationException(ErrorCode.LECTURER_NOT_FOUND));

        if (lecturer.getUserId() == null) {
            throw new LecturerAccountNotificationException(ErrorCode.USER_NOT_FOUND);
        }

        // TODO: Có thể chuyển gửi email sang Spring Event + @Async,
        //  @TransactionalEventListener(AFTER_COMMIT), kết hợp Outbox Pattern khi cần.

        // TODO(NOTIFICATION_STORAGE): Tạo/gửi lại tài khoản GV hiện chỉ trả kết quả ngay trong response,
        //  không lưu lâu dài và chưa retry tự động; sau thao tác không tra cứu lại được kết quả gửi.
        //  Khi cần theo dõi/retry, mở rộng nguồn Notification trước khi tích hợp; không tạo hồ sơ nhập học giả
        //  và không dựng lại lịch sử cho các lần gửi chưa lưu. Hàng đợi nhập học hiện tại vẫn hoạt động riêng.

        AccountNotificationResponse notificationResult =
                identityFacade.sendLecturerAccountNotification(lecturer.getUserId());

        AccountNotificationStatus notificationStatus =
                notificationResult.emailSent() ? AccountNotificationStatus.SENT : AccountNotificationStatus.FAILED;

        // TODO(AUDIT_LOG): Bổ sung lại nhật ký gửi/gửi lại thông báo tài khoản giảng viên sau.

        return new LecturerNotificationResponse(
                lecturerId, lecturer.getLecturerCode(), notificationStatus.name(), notificationResult.emailError());
    }
}
