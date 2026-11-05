package com.eduhub.edu_hub.curriculum.application.facade.dto;

import lombok.*;

@Data
@Builder
public class CurriculumOpeningSubjectResponse {
    private String curriculumCode;
    private Long subjectId;
    private String subjectCode;
    private String subjectName;
    private int credits;
    private int recommendSemester;
}
