package com.eduhub.edu_hub.admission.api.dto.request;

import jakarta.validation.constraints.*;
import java.util.List;
import lombok.Builder;

@Builder
public record PrepareAdmissionClassesRequest(
        @NotNull @Positive Long planId,
        @Min(0) long expectedRevision,
        @Min(1) @Max(99) int classCount,
        List<@NotNull @Positive Long> existingClassIds,
        boolean acceptReserve,
        boolean acceptBelowMinimum,
        @Size(max = 500) String reason) {}
