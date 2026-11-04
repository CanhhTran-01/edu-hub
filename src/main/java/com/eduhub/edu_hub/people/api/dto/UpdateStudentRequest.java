package com.eduhub.edu_hub.people.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
public class UpdateStudentRequest {
    private String studentCode;
    private String fullName;

    @Past
    private LocalDate dateOfBirth;

    @Email
    private String email;

    private String phone;
    private String citizenId;

    @Positive
    private Long administrativeClassId;

    @DecimalMin("0.0")
    private BigDecimal targetGpa;

    private String academicWarningStatus;
    private String academicWarningReason;

    private String statusReason;
}
