package com.eduhub.edu_hub.curriculum.application.facade.dto;

import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;

public record CurriculumAssignmentResponse(
        Long curriculumId, String curriculumCode, Long majorId, Long cohortId, TrainingType trainingType) {}
