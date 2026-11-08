package com.eduhub.edu_hub.identity.api.dto.response;

public record AccountNotificationResponse(Long userId, boolean emailSent, String emailError) {}
