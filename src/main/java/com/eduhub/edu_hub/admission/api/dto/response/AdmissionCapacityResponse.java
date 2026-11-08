package com.eduhub.edu_hub.admission.api.dto.response;

import lombok.Builder;

@Builder
public record AdmissionCapacityResponse(int minimum, int maximum, int reserve) {}
