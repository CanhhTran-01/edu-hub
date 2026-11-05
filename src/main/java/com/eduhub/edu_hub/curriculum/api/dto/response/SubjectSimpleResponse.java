package com.eduhub.edu_hub.curriculum.api.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubjectSimpleResponse {
    private Long id;
    private String subjectCode;
    private String subjectName;
    private int credits;
}
