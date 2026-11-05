package com.eduhub.edu_hub.enrollment.application.facade.dto;

public record LearningAttemptResponse(
        long studentId, String subjectCode, int academicYearStart, int semesterNumber, String status) {}
