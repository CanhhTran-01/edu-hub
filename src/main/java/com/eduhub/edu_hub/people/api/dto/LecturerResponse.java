package com.eduhub.edu_hub.people.api.dto;

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
    private String facultyName;
    private String status;
}
