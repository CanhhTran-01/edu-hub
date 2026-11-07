package com.eduhub.edu_hub.people.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class AdmissionResponses {
    private AdmissionResponses() {}

    public record Capacity(int minimum, int maximum, int reserve) {}

    public record Scenario(
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

    public record PlanResponse(
            Long id,
            long revision,
            Long majorId,
            Long cohortId,
            int totalStudents,
            Capacity capacity,
            String state,
            Integer selectedClassCount,
            List<Scenario> scenarios,
            List<ClassPreview> classes,
            List<ApplicantResponse> applicants,
            BigDecimal averageScoreDifference,
            List<String> warnings) {}

    public record ClassPreview(
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

    public record ApplicantResponse(
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

    public record ConfirmationResponse(
            Long planId,
            long revision,
            String state,
            int enrolled,
            int failed,
            int skipped,
            List<ApplicantResponse> applicants) {}

    public record NotificationResponse(Long id, Long admissionId, String state, int failureCount, String error) {}
}
