package com.eduhub.edu_hub.curriculum.api.dto;

import com.eduhub.edu_hub.curriculum.domain.enums.SubjectStatus;
import lombok.Data;

@Data
public class UpdateSubjectRequest {
    private String subjectName;
    private Integer credits;
    private String description;
    private SubjectStatus status; // ACTIVE / INACTIVE
}
