package com.eduhub.edu_hub.notification.api.dto.response;

import lombok.Builder;

@Builder
public record AdmissionNotificationResponse(Long id, Long admissionId, String state, int failureCount, String error) {}
