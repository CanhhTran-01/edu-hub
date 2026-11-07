package com.eduhub.edu_hub.notification.application.facade.dto;

import lombok.Builder;

@Builder
public record AdmissionNotificationInfo(Long id, Long admissionId, String state, int failureCount, String error) {}
