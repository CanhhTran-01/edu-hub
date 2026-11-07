package com.eduhub.edu_hub.people.application.facade.dto;

import lombok.Builder;

@Builder
public record AdmissionCohortInfo(Long id, String cohortCode, Integer codeSequence) {}
