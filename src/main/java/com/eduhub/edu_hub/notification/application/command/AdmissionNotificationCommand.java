package com.eduhub.edu_hub.notification.application.command;

import lombok.Builder;

@Builder
public record AdmissionNotificationCommand(Long admissionId, Long studentId, String recipient, boolean reEnrollment) {}
