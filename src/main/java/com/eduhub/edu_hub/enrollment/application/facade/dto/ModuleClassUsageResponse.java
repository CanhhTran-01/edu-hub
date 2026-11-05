package com.eduhub.edu_hub.enrollment.application.facade.dto;

public record ModuleClassUsageResponse(
        boolean historyComplete, long enrollmentCount, long studentCount, String dataSource) {}
