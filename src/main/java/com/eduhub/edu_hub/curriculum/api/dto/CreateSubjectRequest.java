package com.eduhub.edu_hub.curriculum.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateSubjectRequest {
    @NotBlank
    private String subjectCode;

    @NotBlank
    private String subjectName;

    @Min(1)
    @Max(10)
    private int credits;

    private String description;
}
