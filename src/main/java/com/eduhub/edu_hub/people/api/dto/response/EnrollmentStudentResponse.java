package com.eduhub.edu_hub.people.api.dto.response;

import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;

public record EnrollmentStudentResponse(
        Long studentId,
        String studentCode,
        String fullName,
        Long majorId,
        Long cohortId,
        TrainingType trainingType,
        Integer admissionYear,
        ActivityStatus activityStatus) {}
