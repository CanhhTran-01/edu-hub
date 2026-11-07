package com.eduhub.edu_hub.people.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class AdmissionRequests {
    private AdmissionRequests() {}

    public record PlanRequest(
            @NotNull @Positive Long majorId,
            @NotNull @Positive Long cohortId,
            @Min(0) int totalStudents,
            @Min(1) @Max(99) Integer minCapacity,
            @Min(1) @Max(99) Integer maxCapacity,
            @Min(0) @Max(98) Integer reserveCapacity) {}

    public record CreateClassesRequest(
            @NotNull @Positive Long planId,
            @Min(0) long expectedRevision,
            @Min(1) @Max(99) int classCount,
            List<@NotNull @Positive Long> existingClassIds,
            boolean acceptReserve,
            boolean acceptBelowMinimum,
            @Size(max = 500) String reason) {}

    public record PreviewRequest(@Min(0) long expectedRevision) {}

    public record Assignment(@NotNull @Positive Long applicantId, @NotNull @Positive Long classId) {}

    public record AdjustAssignmentsRequest(
            @Min(0) long expectedRevision, @NotEmpty List<@NotNull @Valid Assignment> assignments) {}

    public record ConfirmRequest(
            @Min(0) long expectedRevision,
            boolean acceptReserve,
            boolean acceptBelowMinimum,
            @Size(max = 500) String reason) {}
}
