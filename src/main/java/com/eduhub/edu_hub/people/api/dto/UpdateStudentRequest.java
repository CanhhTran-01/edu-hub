package com.eduhub.edu_hub.people.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
public class UpdateStudentRequest {
    private String fullName;
    private LocalDate dateOfBirth;
    private String email;
    private String phone;
    private String citizenId;
    private Long administrativeClassId;
    private BigDecimal targetGpa;
    private String statusReason;
}
