package com.eduhub.edu_hub.people.api.dto.response;

import com.eduhub.edu_hub.people.domain.enums.LecturerStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LecturerResponse {
    private Long id;
    private String lecturerCode;
    private String fullName;
    private String email;
    private String phone;
    private java.time.LocalDate dateOfBirth;
    private String citizenId;
    private Long facultyId;
    private String facultyName;
    private LecturerStatus status;
}
