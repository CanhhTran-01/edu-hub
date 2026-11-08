package com.eduhub.edu_hub.admission.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

@Builder
public record AdmissionApplicantResponse(
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
