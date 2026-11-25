package com.eduhub.edu_hub.scheduling.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateModuleClassRequest {
    @NotNull(message = "Sức chứa không được để trống")
    @Min(1)
    private Integer maxCapacity;

    @NotBlank(message = "Lý do cập nhật không được để trống")
    private String reason;
}
