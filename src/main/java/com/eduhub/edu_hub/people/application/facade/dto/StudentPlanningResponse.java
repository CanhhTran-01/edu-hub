package com.eduhub.edu_hub.people.application.facade.dto;

import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;

public record StudentPlanningResponse(
        Long id,
        String studentCode,
        Long majorId,
        Long cohortId,
        TrainingType trainingType,
        Integer admissionYear,
        ActivityStatus activityStatus) {}
