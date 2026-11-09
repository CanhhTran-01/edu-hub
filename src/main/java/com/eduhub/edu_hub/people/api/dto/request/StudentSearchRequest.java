package com.eduhub.edu_hub.people.api.dto.request;

import lombok.Data;

@Data
public class StudentSearchRequest {
    private String keyword; // MSSV hoặc họ tên
    private Long majorId;
    private Long cohortId;
    private Long classId;
    private String activityStatus;
    private int page = 0;
    private int size = 20;
}
