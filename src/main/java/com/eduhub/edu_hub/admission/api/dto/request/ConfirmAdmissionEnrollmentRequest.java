package com.eduhub.edu_hub.admission.api.dto.request;

import jakarta.validation.constraints.*;
import lombok.Builder;

@Builder
public record ConfirmAdmissionEnrollmentRequest(
        @Min(0) long expectedRevision,
        boolean acceptReserve,
        boolean acceptBelowMinimum,
        @Size(max = 500) String reason) {}
