package com.eduhub.edu_hub.notification.application.command;

import lombok.Builder;

@Builder
public record QueueAdmissionNotificationCommand(
        Long admissionId, Long studentId, String recipient, boolean reEnrollment) {}
