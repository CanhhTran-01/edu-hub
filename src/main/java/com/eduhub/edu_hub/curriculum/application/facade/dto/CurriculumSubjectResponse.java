package com.eduhub.edu_hub.curriculum.application.facade.dto;

import com.eduhub.edu_hub.curriculum.domain.enums.SubjectType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CurriculumSubjectResponse {
    private Long subjectId;
    private String subjectCode;
    private String subjectName;
    private int credits;
    private int recommendSemester;
    private SubjectType subjectType; // REQUIRED / ELECTIVE
}
