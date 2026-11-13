package com.eduhub.edu_hub.enrollment.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class RegisterModuleClassRequest {
    @NotNull
    @Positive
    private Long moduleClassId;
}
