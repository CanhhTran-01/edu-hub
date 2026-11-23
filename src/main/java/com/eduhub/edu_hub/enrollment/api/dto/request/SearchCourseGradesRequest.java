package com.eduhub.edu_hub.enrollment.api.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SearchCourseGradesRequest {
    private String keyword;
    private Long semesterId;
    private Long subjectId;
    private Long moduleClassId;
    private int page = 0;
    private int size = 20;
}
