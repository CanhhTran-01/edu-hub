package com.eduhub.edu_hub.identity.application.facade;

import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.command.CreateLecturerAccountCommand;
import com.eduhub.edu_hub.identity.application.command.CreateStudentAccountCommand;
import com.eduhub.edu_hub.identity.application.result.CreateStudentAccountResult;

public interface IdentityFacade {
    Long createLecturerAccountWithoutNotification(CreateLecturerAccountCommand command);

    CreateStudentAccountResult sendLecturerAccountNotification(Long userId, String initialPassword);

    CreateStudentAccountResult createStudentAccount(CreateStudentAccountCommand command);

    Long createStudentAccountWithoutNotification(CreateStudentAccountCommand command);

    CreateStudentAccountResult sendStudentAccountNotification(Long userId, String initialPassword);

    UserInfoResponse requireCurrentUser(String accessToken);

    /** Kiểm tra tồn tại */
    boolean existsByUsername(String username);
}
