package com.eduhub.edu_hub.people.application.facade.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;

@Builder
public record AdmissionStudentRegistration(
        Long classId,
        String citizenId,
        String fullName,
        LocalDate dateOfBirth,
        String gender,
        String email,
        String phone,
        BigDecimal entranceScore) {}
