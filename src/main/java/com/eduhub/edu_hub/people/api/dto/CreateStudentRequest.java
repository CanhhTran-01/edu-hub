package com.eduhub.edu_hub.people.api.dto;

import com.eduhub.edu_hub.shared.enums.TrainingType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
public class CreateStudentRequest {
    @NotBlank
    @Size(max = 255)
    private String fullName;

    private LocalDate dateOfBirth;
    private String gender;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @Size(max = 255)
    private String phone;

    @Size(max = 255)
    private String citizenId;

    @NotNull
    private Long majorId;

    @NotNull
    private Long cohortId;

    @NotNull
    private Long administrativeClassId;

    @NotNull
    private TrainingType trainingType;

    @DecimalMin("0.0")
    private BigDecimal entranceScore;
    @NotBlank
    private String reason;
}
