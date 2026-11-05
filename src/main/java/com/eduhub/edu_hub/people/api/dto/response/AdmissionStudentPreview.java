package com.eduhub.edu_hub.people.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AdmissionStudentPreview(
        int rowNumber,
        String applicationCode,
        String fullName,
        LocalDate dateOfBirth,
        String gender,
        String email,
        BigDecimal entranceScore,
        String majorCode,
        String cohortCode,
        String trainingType,
        Long assignedClassId,
        String assignedClassName) {}
