package com.eduhub.edu_hub.people.api.dto;

import lombok.Data;

@Data
public class StudentSearchRequest {
    private String keyword; // MSSV / họ tên
    private Long majorId;
    private Long cohortId;
    private Long classId;
    private String activityStatus;
    private String academicWarningStatus;
}
