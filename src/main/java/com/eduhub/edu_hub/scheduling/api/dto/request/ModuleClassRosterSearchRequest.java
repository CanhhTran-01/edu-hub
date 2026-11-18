package com.eduhub.edu_hub.scheduling.api.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class ModuleClassRosterSearchRequest {
    private String keyword;

    @Min(0)
    private int page = 0;

    @Min(1)
    private int size = 20;
}
