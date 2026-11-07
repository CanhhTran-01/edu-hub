package com.eduhub.edu_hub.admission.application.result;

import com.eduhub.edu_hub.shared.enums.TrainingType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

public final class AdmissionResults {
    private AdmissionResults() {}

    @Builder
    public record CapacityResult(int minimum, int maximum, int reserve) {}

    @Builder
    public record ScenarioResult(
            int classCount,
            List<Integer> targetSizes,
            String level,
            int reserveUsed,
            boolean belowMinimum,
            boolean insufficientRooms,
            long suitableRooms,
            int requiredRoomCapacity,
            boolean recommended,
            List<String> warnings) {}

    @Builder
    public record PlanResult(
            Long id,
            long revision,
            Long majorId,
            Long cohortId,
            int totalStudents,
            CapacityResult capacity,
            String state,
            Integer selectedClassCount,
            List<ScenarioResult> scenarios,
            List<ClassResult> classes,
            List<ApplicantResult> applicants,
            BigDecimal averageScoreDifference,
            List<String> warnings) {}

    @Builder
    public record ClassResult(
            Long id,
            String className,
            int targetSize,
            int assignedSize,
            int currentSize,
            int minimum,
            int maximum,
            int reserve,
            int reserveUsed,
            BigDecimal averageScore,
            List<Long> applicantIds) {}

    @Builder
    public record ApplicantResult(
            Long id,
            int rowNumber,
            String applicationCode,
            String citizenId,
            String fullName,
            LocalDate dateOfBirth,
            String gender,
            String email,
            String phone,
            BigDecimal entranceScore,
            String state,
            String error,
            Long assignedClassId,
            Long studentId,
            String studentCode,
            boolean reusingAccount,
            List<String> warnings) {}

    @Builder
    public record ConfirmationResult(
            Long planId,
            long revision,
            String state,
            int enrolled,
            int failed,
            int skipped,
            List<ApplicantResult> applicants) {}

    @Builder
    public record NotificationResult(Long id, Long admissionId, String state, int failureCount, String error) {}

    @Builder
    public record AdministrativeClassResult(
            Long id,
            String className,
            String majorCode,
            String majorName,
            String cohortCode,
            TrainingType trainingType,
            Integer currentSize,
            Integer maxCapacity,
            Integer minCapacity,
            Integer reserveCapacity,
            boolean availableForAdmission) {}
}
