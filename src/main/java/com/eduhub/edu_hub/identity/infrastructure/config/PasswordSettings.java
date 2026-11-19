package com.eduhub.edu_hub.identity.infrastructure.config;

import com.eduhub.edu_hub.identity.application.exception.IdentityConfigurationException;
import com.eduhub.edu_hub.identity.domain.policy.PasswordPolicy;
import java.nio.charset.StandardCharsets;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Cấu hình mật khẩu dùng chung cho cấp tài khoản và gửi lại thông tin tài khoản. */
@Component
@Getter
public class PasswordSettings {
    private final String initialPassword;

    public PasswordSettings(@Value("${identity.password.initial-password}") String initialPassword) {

        // Mật khẩu do hệ thống cấp không kiểm tra độ mạnh, nhưng vẫn phải hợp lệ với BCrypt.
        if (initialPassword == null
                || initialPassword.isBlank()
                || initialPassword.getBytes(StandardCharsets.UTF_8).length > PasswordPolicy.MAX_PASSWORD_BYTES) {
            throw new IdentityConfigurationException(
                    "Mật khẩu khởi tạo phải khác rỗng và không vượt quá 72 byte UTF-8");
        }

        this.initialPassword = initialPassword;
    }
}
