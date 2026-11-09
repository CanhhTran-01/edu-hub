package com.eduhub.edu_hub.identity.application.facade;

import com.eduhub.edu_hub.identity.api.dto.request.CreateLecturerAccountRequest;
import com.eduhub.edu_hub.identity.api.dto.request.CreateStudentAccountRequest;
import com.eduhub.edu_hub.identity.api.dto.response.AccountNotificationResponse;
import com.eduhub.edu_hub.identity.api.dto.response.StudentAccountResponse;
import com.eduhub.edu_hub.identity.api.dto.response.UserAccountEmailResponse;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import java.util.List;
import java.util.Set;
import org.springframework.security.oauth2.jwt.Jwt;

public interface IdentityFacade {
    Long createLecturerAccountWithoutNotification(CreateLecturerAccountRequest request);

    AccountNotificationResponse sendLecturerAccountNotification(Long userId);

    Long createStudentAccountWithoutNotification(CreateStudentAccountRequest request);

    AccountNotificationResponse sendStudentAccountNotification(Long userId);

    StudentAccountResponse getStudentAccount(Long userId);

    AccountNotificationResponse sendStudentAdmissionNotification(Long userId, String recipient);

    UserInfoResponse requireCurrentUser(Jwt authenticatedJwt);

    /** Cập nhật nguồn email duy nhất trong transaction hồ sơ do People quản lý; null xóa email. */
    void updateAccountEmail(Long userId, String email);

    /** Đọc theo lô, giữ cả tài khoản đã xóa để hồ sơ lịch sử không mất email; không có User thì không có phần tử. */
    List<UserAccountEmailResponse> findAccountEmailsByUserIds(Set<Long> userIds);

    /** Tìm User ID có email tương ứng để People kiểm tra trùng trên đúng nhóm hồ sơ giảng viên. */
    Set<Long> findUserIdsByEmail(String email);

    /** Kiểm tra tồn tại */
    boolean existsByUsername(String username);
}
