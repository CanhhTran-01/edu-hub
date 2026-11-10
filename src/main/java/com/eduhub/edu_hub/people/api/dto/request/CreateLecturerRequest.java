package com.eduhub.edu_hub.people.api.dto.request;

import com.eduhub.edu_hub.shared.text.TextsNormalizer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

@Data
public class CreateLecturerRequest {
    @NotBlank
    @jakarta.validation.constraints.Size(max = 255)
    private String fullName;

    @NotNull
    @jakarta.validation.constraints.Past
    private LocalDate dateOfBirth;

    @NotBlank
    @jakarta.validation.constraints.Email
    @jakarta.validation.constraints.Size(max = 255)
    private String email;

    public void setEmail(String email) {
        this.email = TextsNormalizer.normalizeEmail(email);
    }

    @jakarta.validation.constraints.Size(max = 255)
    private String phone;

    @jakarta.validation.constraints.Size(max = 255)
    private String citizenId;

    @NotNull
    private Long facultyId;

    @NotNull
    @Min(1000)
    @Max(9999)
    private Integer recruitmentYear;

    @NotEmpty
    private List<@NotNull @Positive Long> teachingSubjectIds;

    @NotBlank
    @jakarta.validation.constraints.Size(max = 500)
    private String reason;
}
