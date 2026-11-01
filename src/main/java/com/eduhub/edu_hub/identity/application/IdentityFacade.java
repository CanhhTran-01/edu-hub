package com.eduhub.edu_hub.identity.application;

import com.eduhub.edu_hub.identity.api.dto.UserInfoResponse;
import java.util.Optional;

public interface IdentityFacade {
    /** Tạo tài khoản mới (dùng khi tạo SV/GV) */
    Long createUser(String username, String rawPassword, String roleName, boolean isFirstLogin);

    /** Khóa / mở khóa tài khoản */
    void lockUser(Long userId, String reason);

    void unlockUser(Long userId);

    /** Vô hiệu hóa / kích hoạt tài khoản (đồng bộ với trạng thái SV/GV) */
    void disableUser(Long userId);

    void enableUser(Long userId);

    /** Đặt lại mật khẩu tạm thời */
    String resetPassword(Long userId);

    /** Lấy thông tin cơ bản */
    Optional<UserInfoResponse> findById(Long userId);

    Optional<UserInfoResponse> findByUsername(String username);

    /** Kiểm tra tồn tại */
    boolean existsByUsername(String username);
}
