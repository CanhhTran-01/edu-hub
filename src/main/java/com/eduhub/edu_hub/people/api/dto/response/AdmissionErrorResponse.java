package com.eduhub.edu_hub.people.api.dto.response;

public record AdmissionErrorResponse(int rowNumber, String applicationCode, String field, String reason) {}
