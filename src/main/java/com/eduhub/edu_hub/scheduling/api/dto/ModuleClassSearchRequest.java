package com.eduhub.edu_hub.scheduling.api.dto;

import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ModuleClassSearchRequest {
    private String keyword;

    @Positive
    private Long semesterId;

    @Positive
    private Long subjectId;

    @Positive
    private Long lecturerId;

    private ModuleClassStatus status;

    @Min(0)
    private int page = 0;

    @Min(1)
    private int size = 20;
}
