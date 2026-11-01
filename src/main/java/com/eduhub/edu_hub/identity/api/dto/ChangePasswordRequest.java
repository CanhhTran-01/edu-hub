package com.eduhub.edu_hub.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {
    private String currentPassword; // null khi first-login

    @NotBlank
    @Size(min = 8)
    private String newPassword;

    @NotBlank
    private String confirmPassword;
}
