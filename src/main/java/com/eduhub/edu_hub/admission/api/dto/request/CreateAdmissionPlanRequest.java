package com.eduhub.edu_hub.admission.api.dto.request;

import jakarta.validation.constraints.*;
import lombok.Builder;

@Builder
public record CreateAdmissionPlanRequest(
        @NotNull @Positive Long majorId,
        @NotNull @Positive Long cohortId,
        @Min(0) int totalStudents,
        @Min(1) @Max(99) Integer minCapacity,
        @Min(1) @Max(99) Integer maxCapacity,
        @Min(0) @Max(98) Integer reserveCapacity) {}
