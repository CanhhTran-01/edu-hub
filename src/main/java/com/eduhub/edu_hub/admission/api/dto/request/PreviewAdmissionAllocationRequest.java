package com.eduhub.edu_hub.admission.api.dto.request;

import jakarta.validation.constraints.*;
import lombok.Builder;

@Builder
public record PreviewAdmissionAllocationRequest(@Min(0) long expectedRevision) {}
