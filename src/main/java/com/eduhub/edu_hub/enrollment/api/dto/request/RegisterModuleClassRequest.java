package com.eduhub.edu_hub.enrollment.api.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegisterModuleClassRequest {
    @NotNull
    private Long moduleClassId;
}
