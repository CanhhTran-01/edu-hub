package com.eduhub.edu_hub.notification.infrastructure.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "notification.mail")
public class NotificationMailSettings {
    @NotBlank
    private String brandName = "EduHub";

    // Chưa có URL frontend thì ẩn nút đăng nhập, không gửi đường dẫn giả cho người nhận.
    @Pattern(regexp = "^$|https?://\\S+")
    private String loginUrl = "";

    @Email
    private String supportEmail = "";
}
