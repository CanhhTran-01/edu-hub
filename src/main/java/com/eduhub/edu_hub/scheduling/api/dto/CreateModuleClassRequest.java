package com.eduhub.edu_hub.scheduling.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateModuleClassRequest {
    @NotNull
    private Long subjectId;

    @NotNull
    private Long semesterId;

    @NotNull
    private Long lecturerId;

    @NotBlank
    private String moduleClassCode; // L01, L02...

    @Min(1)
    private int maxCapacity;
}
