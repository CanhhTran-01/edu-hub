package com.eduhub.edu_hub.scheduling.api.dto.request;

import com.eduhub.edu_hub.scheduling.api.serialization.ClassCapacityDeserializer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import lombok.Data;
import tools.jackson.databind.annotation.JsonDeserialize;

@Data
public class CreateModuleClassRequest {
    @Min(1)
    @JsonDeserialize(using = ClassCapacityDeserializer.class)
    private Integer maxCapacity;

    @JsonIgnore
    private boolean maxCapacityPresent;

    public void setMaxCapacity(Integer maxCapacity) {
        this.maxCapacity = maxCapacity;
        this.maxCapacityPresent = true;
    }

    @JsonIgnore
    @AssertTrue(message = "Sức chứa không được là null khi đã gửi field")
    public boolean isMaxCapacitySpecifiedCorrectly() {
        return !maxCapacityPresent || maxCapacity != null;
    }
}
