package com.eduhub.edu_hub.people.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;

@Data
public class CreateLecturerRequest {
    @NotBlank
    private String fullName;

    private LocalDate dateOfBirth;
    private String email;
    private String phone;
    private String citizenId;

    @NotNull
    private Long facultyId;

    private String reason;
}
