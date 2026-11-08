package com.eduhub.edu_hub.notification.application.command;

import lombok.Builder;

@Builder
public record SendAdmissionConfirmationEmailCommand(String recipient, String username) {}
