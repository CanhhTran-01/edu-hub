package com.eduhub.edu_hub.identity.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LockUserRequest(
        @NotBlank(message = "Lý do khóa tài khoản không được để trống")
                @Size(max = 500, message = "Lý do khóa tài khoản tối đa 500 ký tự")
                String reason) {
    public LockUserRequest {
        reason = reason == null ? null : reason.trim();
    }
}
