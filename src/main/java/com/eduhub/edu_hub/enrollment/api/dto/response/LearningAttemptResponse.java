package com.eduhub.edu_hub.enrollment.api.dto.response;

public record LearningAttemptResponse(
        long studentId, String subjectCode, int academicYearStart, int semesterNumber, String status) {}
