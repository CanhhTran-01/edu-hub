package com.eduhub.edu_hub.people.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
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

    @jakarta.validation.constraints.Size(max = 255)
    private String phone;

    @jakarta.validation.constraints.Size(max = 255)
    private String citizenId;

    @NotNull
    private Long facultyId;

    @NotBlank
    @jakarta.validation.constraints.Size(max = 500)
    private String reason;
}
