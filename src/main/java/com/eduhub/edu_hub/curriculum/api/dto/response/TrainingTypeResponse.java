package com.eduhub.edu_hub.curriculum.api.dto.response;

import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;

public record TrainingTypeResponse(TrainingType trainingType, String label, String prefix, boolean hasPrefixMapping) {}
