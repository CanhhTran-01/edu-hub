package com.eduhub.edu_hub.scheduling.api.dto.request;

import jakarta.validation.constraints.*;
import java.util.List;
import lombok.Data;

@Data
public class ModuleClassPublicationRequest {
    private boolean selectAll;

    @NotNull
    private List<@NotNull @Positive Long> moduleClassIds = List.of();
}
