package com.eduhub.edu_hub.identity.application.result;

public record AccountNotificationResult(Long userId, boolean emailSent, String emailError) {}
