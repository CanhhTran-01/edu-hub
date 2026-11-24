package com.eduhub.edu_hub.curriculum.api.dto.response;

import com.eduhub.edu_hub.curriculum.domain.enums.CatalogStatus;

public record MajorResponse(Long id, String majorCode, String majorName, CatalogStatus status) {}
