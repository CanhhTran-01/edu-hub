package com.eduhub.edu_hub.scheduling.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CancelModuleClassRequest {
    @NotBlank(message = "Lý do hủy lớp không được để trống")
    private String reason;
}
