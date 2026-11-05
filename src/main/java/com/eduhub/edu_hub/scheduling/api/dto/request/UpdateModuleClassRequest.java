package com.eduhub.edu_hub.scheduling.api.dto.request;

import com.eduhub.edu_hub.scheduling.api.serialization.ClassCapacityDeserializer;
import com.eduhub.edu_hub.scheduling.domain.policy.ModuleClassPolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import tools.jackson.databind.annotation.JsonDeserialize;

@Data
public class UpdateModuleClassRequest {
    @NotNull(message = "Sức chứa không được để trống")
    @Min(1)
    @Max(ModuleClassPolicy.MAX_CAPACITY)
    @JsonDeserialize(using = ClassCapacityDeserializer.class)
    private Integer maxCapacity;

    @NotBlank(message = "Lý do cập nhật không được để trống")
    private String reason;
}
