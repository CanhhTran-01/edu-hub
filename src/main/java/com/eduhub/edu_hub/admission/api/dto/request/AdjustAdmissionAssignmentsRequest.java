package com.eduhub.edu_hub.admission.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.Builder;

@Builder
public record AdjustAdmissionAssignmentsRequest(
        @Min(0) long expectedRevision, @NotEmpty List<@NotNull @Valid AdmissionAssignmentRequest> assignments) {}
