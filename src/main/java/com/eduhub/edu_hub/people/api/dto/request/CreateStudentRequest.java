package com.eduhub.edu_hub.people.api.dto.request;

import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.shared.text.TextsNormalizer;
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

    @jakarta.validation.constraints.Past
    private LocalDate dateOfBirth;

    private String gender;

    @Email
    @Size(max = 255)
    private String email;

    public void setEmail(String email) {
        this.email = TextsNormalizer.normalizeEmail(email);
    }

    @Size(max = 255)
    private String phone;

    @NotBlank
    @Size(max = 32)
    private String citizenId;

    @NotNull
    private Long majorId;

    @NotNull
    private Long cohortId;

    @NotNull
    private Long administrativeClassId;

    private TrainingType trainingType = TrainingType.CHINH_QUY;

    @DecimalMin("0.0")
    private BigDecimal entranceScore;

    @NotBlank
    private String reason;
}
