package com.eduhub.edu_hub.scheduling.api.dto.request;

import com.eduhub.edu_hub.scheduling.api.serialization.ClassCapacityDeserializer;
import com.eduhub.edu_hub.scheduling.domain.policy.ModuleClassPolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import tools.jackson.databind.annotation.JsonDeserialize;

@Data
public class CreateModuleClassRequest {
    @NotNull
    @Min(1)
    @Max(ModuleClassPolicy.MAX_CAPACITY)
    @JsonDeserialize(using = ClassCapacityDeserializer.class)
    private Integer maxCapacity = ModuleClassPolicy.MAX_CAPACITY;
}
