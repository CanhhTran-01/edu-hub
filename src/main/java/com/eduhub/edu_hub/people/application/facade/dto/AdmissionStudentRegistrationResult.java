package com.eduhub.edu_hub.people.application.facade.dto;

import lombok.Builder;

@Builder
public record AdmissionStudentRegistrationResult(AdmissionStudentInfo student, boolean reused, boolean accountLocked) {}
