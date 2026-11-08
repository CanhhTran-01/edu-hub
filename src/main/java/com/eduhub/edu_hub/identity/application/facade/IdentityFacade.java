package com.eduhub.edu_hub.identity.application.facade;

import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.command.CreateLecturerAccountCommand;
import com.eduhub.edu_hub.identity.application.command.CreateStudentAccountCommand;
import com.eduhub.edu_hub.identity.application.result.AccountNotificationResult;
import com.eduhub.edu_hub.identity.application.result.StudentAccountInfo;

public interface IdentityFacade {
    Long createLecturerAccountWithoutNotification(CreateLecturerAccountCommand command);

    AccountNotificationResult sendLecturerAccountNotification(Long userId, String initialPassword);

    Long createStudentAccountWithoutNotification(CreateStudentAccountCommand command);

    AccountNotificationResult sendStudentAccountNotification(Long userId, String initialPassword);

    StudentAccountInfo getStudentAccount(Long userId);

    AccountNotificationResult sendStudentAdmissionNotification(Long userId, String recipient);

    UserInfoResponse requireCurrentUser(String accessToken);

    /** Kiểm tra tồn tại */
    boolean existsByUsername(String username);
}
