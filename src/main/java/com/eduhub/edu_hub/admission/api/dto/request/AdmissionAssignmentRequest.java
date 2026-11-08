package com.eduhub.edu_hub.admission.api.dto.request;

import jakarta.validation.constraints.*;
import lombok.Builder;

@Builder
public record AdmissionAssignmentRequest(@NotNull @Positive Long applicantId, @NotNull @Positive Long classId) {}
