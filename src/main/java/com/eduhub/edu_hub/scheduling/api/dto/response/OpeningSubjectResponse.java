package com.eduhub.edu_hub.scheduling.api.dto.response;

import lombok.*;

@Data
@Builder
public class OpeningSubjectResponse {
    private Long subjectId;
    private String subjectCode;
    private String subjectName;
    private int credits;
    private int onTimeStudentCount;
    private int retakeStudentCount;
    private int missedStudentCount;
    private int expectedStudentCount;
    private int recommendedClassCount;
    private long existingClassCount;
    private String dataSource;
    private boolean historyComplete;
}
