package com.eduhub.edu_hub.people.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdmissionClassAdjustmentRequest(
        @NotBlank String applicationCode, @NotNull Long targetClassId) {}
