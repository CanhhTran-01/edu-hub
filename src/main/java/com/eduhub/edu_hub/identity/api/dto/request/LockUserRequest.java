package com.eduhub.edu_hub.identity.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LockUserRequest {
    @NotNull
    private Long userId;

    @NotBlank
    private String reason;
}
