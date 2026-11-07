package com.eduhub.edu_hub.admission.application.command;

import lombok.Builder;

@Builder
public record AdmissionImportScope(String majorCode, String majorName, String cohortCode) {}
