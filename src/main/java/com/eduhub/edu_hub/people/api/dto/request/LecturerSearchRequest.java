package com.eduhub.edu_hub.people.api.dto.request;

import lombok.Data;

@Data
public class LecturerSearchRequest {
    private String keyword;
    private String lecturerCode;
    private String fullName;
    private Long facultyId;
    private String status;
}
