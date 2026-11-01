package com.eduhub.edu_hub.people.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
public class CreateStudentRequest {
    @NotBlank
    private String fullName;

    private LocalDate dateOfBirth;
    private String email;
    private String phone;
    private String citizenId;

    @NotNull
    private Long majorId;

    @NotNull
    private Long cohortId;

    @NotNull
    private Long administrativeClassId;

    private BigDecimal entranceScore;
    private String reason; // lý do thêm mới
}
