package com.eduhub.edu_hub.curriculum.api.dto;

import com.eduhub.edu_hub.shared.enums.TrainingType;

public record CurriculumAssignmentResponse(
        Long curriculumId, String curriculumCode, Long majorId, Long cohortId, TrainingType trainingType) {}
