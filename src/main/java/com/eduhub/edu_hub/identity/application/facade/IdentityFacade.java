package com.eduhub.edu_hub.identity.application.facade;

import com.eduhub.edu_hub.identity.api.dto.request.CreateLecturerAccountRequest;
import com.eduhub.edu_hub.identity.api.dto.request.CreateStudentAccountRequest;
import com.eduhub.edu_hub.identity.api.dto.response.AccountNotificationResponse;
import com.eduhub.edu_hub.identity.api.dto.response.StudentAccountResponse;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;

public interface IdentityFacade {
    Long createLecturerAccountWithoutNotification(CreateLecturerAccountRequest request);

    AccountNotificationResponse sendLecturerAccountNotification(Long userId);

    Long createStudentAccountWithoutNotification(CreateStudentAccountRequest request);

    AccountNotificationResponse sendStudentAccountNotification(Long userId);

    StudentAccountResponse getStudentAccount(Long userId);

    AccountNotificationResponse sendStudentAdmissionNotification(Long userId, String recipient);

    UserInfoResponse requireCurrentUser(String accessToken);

    /** Kiểm tra tồn tại */
    boolean existsByUsername(String username);
}
