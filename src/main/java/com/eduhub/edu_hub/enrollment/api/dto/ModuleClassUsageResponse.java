package com.eduhub.edu_hub.enrollment.api.dto;

public record ModuleClassUsageResponse(
        boolean historyComplete, long enrollmentCount, long studentCount, String dataSource) {}
