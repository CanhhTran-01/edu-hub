package com.eduhub.edu_hub.people.application.facade.dto;

import lombok.Builder;

@Builder
public record AdmissionClassPrefix(Long majorId, String majorCode, String majorName, String prefix) {}
