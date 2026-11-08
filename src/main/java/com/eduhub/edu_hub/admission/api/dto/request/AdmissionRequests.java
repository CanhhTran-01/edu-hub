package com.eduhub.edu_hub.admission.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.Builder;

public final class AdmissionRequests {
    private AdmissionRequests() {}

    @Builder
    public record CreatePlanRequest(
            @NotNull @Positive Long majorId,
            @NotNull @Positive Long cohortId,
            @Min(0) int totalStudents,
            @Min(1) @Max(99) Integer minCapacity,
            @Min(1) @Max(99) Integer maxCapacity,
            @Min(0) @Max(98) Integer reserveCapacity) {}

    @Builder
    public record PrepareClassesRequest(
            @NotNull @Positive Long planId,
            @Min(0) long expectedRevision,
            @Min(1) @Max(99) int classCount,
            List<@NotNull @Positive Long> existingClassIds,
            boolean acceptReserve,
            boolean acceptBelowMinimum,
            @Size(max = 500) String reason) {}

    @Builder
    public record PreviewAllocationRequest(@Min(0) long expectedRevision) {}

    @Builder
    public record Assignment(@NotNull @Positive Long applicantId, @NotNull @Positive Long classId) {}

    @Builder
    public record AdjustAssignmentsRequest(
            @Min(0) long expectedRevision, @NotEmpty List<@NotNull @Valid Assignment> assignments) {}

    @Builder
    public record ConfirmEnrollmentRequest(
            @Min(0) long expectedRevision,
            boolean acceptReserve,
            boolean acceptBelowMinimum,
            @Size(max = 500) String reason) {}
}
