package com.eduhub.edu_hub.people.application.facade.dto;

import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import com.eduhub.edu_hub.shared.enums.TrainingType;

public record StudentPlanningResponse(
        Long id,
        String studentCode,
        Long majorId,
        Long cohortId,
        TrainingType trainingType,
        Integer admissionYear,
        ActivityStatus activityStatus) {}
