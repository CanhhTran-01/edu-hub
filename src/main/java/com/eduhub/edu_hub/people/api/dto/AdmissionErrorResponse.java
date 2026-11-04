package com.eduhub.edu_hub.people.api.dto;

public record AdmissionErrorResponse(int rowNumber, String applicationCode, String field, String reason) {}
