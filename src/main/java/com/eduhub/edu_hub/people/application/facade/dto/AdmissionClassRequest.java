package com.eduhub.edu_hub.people.application.facade.dto;

import lombok.Builder;

@Builder
public record AdmissionClassRequest(Long majorId, Long cohortId, int sequence, int minimum, int maximum, int reserve) {}
