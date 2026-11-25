package com.eduhub.edu_hub.curriculum.api.dto.response;

import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;

public record CurriculumAssignmentResponse(
        Long curriculumId, String curriculumCode, Long majorId, Long cohortId, TrainingType trainingType) {}
