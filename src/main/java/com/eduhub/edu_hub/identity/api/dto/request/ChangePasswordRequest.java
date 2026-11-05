package com.eduhub.edu_hub.identity.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangePasswordRequest {
    private String currentPassword; // null khi first-login

    @NotBlank
    private String newPassword;

    @NotBlank
    private String confirmPassword;
}
