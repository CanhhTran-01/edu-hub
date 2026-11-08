package com.eduhub.edu_hub.notification.api.dto.request;

import lombok.Builder;

@Builder
public record QueueAdmissionNotificationRequest(
        Long admissionId, Long studentId, String recipient, boolean reEnrollment) {}
