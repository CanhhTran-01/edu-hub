package com.eduhub.edu_hub.people.application;

import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.identity.application.result.CreateStudentAccountResult;
import com.eduhub.edu_hub.people.api.dto.response.LecturerNotificationResponse;
import com.eduhub.edu_hub.people.domain.enums.AccountNotificationStatus;
import com.eduhub.edu_hub.people.domain.repository.LecturerRepository;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class LecturerAccountNotificationService {
    private final LecturerRepository lecturerRepository;
    private final IdentityFacade identityFacade;
    private final TransactionTemplate transactionTemplate;

    public LecturerNotificationResponse send(Long lecturerId, String initialPassword) {

        var lecturer = lecturerRepository
                .findByIdAndDeletedFalse(lecturerId)
                .orElseThrow(() -> new BaseException(ErrorCode.LECTURER_NOT_FOUND));

        if (lecturer.getUserId() == null) {
            throw new BaseException(ErrorCode.USER_NOT_FOUND);
        }

        // TODO: Có thể chuyển gửi email sang Spring Event + @Async,
        //  @TransactionalEventListener(AFTER_COMMIT), kết hợp Outbox Pattern khi cần.
        CreateStudentAccountResult result =
                identityFacade.sendLecturerAccountNotification(lecturer.getUserId(), initialPassword);

        AccountNotificationStatus status =
                result.emailSent() ? AccountNotificationStatus.SENT : AccountNotificationStatus.FAILED;

        recordNotificationResult(lecturerId, status, result.emailError());

        return new LecturerNotificationResponse(
                lecturerId, lecturer.getLecturerCode(), status.name(), result.emailError());
    }

    private void recordNotificationResult(Long lecturerId, AccountNotificationStatus status, String error) {

        transactionTemplate.executeWithoutResult(transactionStatus -> {
            var lecturer = lecturerRepository
                    .findByIdAndDeletedFalse(lecturerId)
                    .orElseThrow(() -> new BaseException(ErrorCode.LECTURER_NOT_FOUND));

            lecturer.setAccountNotificationStatus(status);
            lecturer.setAccountNotificationError(error);
            lecturerRepository.save(lecturer);

            // TODO(AUDIT_LOG): Bổ sung lại nhật ký gửi/gửi lại thông báo tài khoản giảng viên sau.
        });
    }
}
