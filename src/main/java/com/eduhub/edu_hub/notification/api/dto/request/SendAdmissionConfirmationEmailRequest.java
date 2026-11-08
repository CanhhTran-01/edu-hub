package com.eduhub.edu_hub.notification.api.dto.request;

import lombok.Builder;

@Builder
public record SendAdmissionConfirmationEmailRequest(String recipient, String username) {}
