package com.eduhub.edu_hub.identity.api.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotNull
    private Long userId;

    private String reason;
}
