package com.eduhub.edu_hub.people.api.dto.response;

public record AccountNotificationResponse(
        Long studentId, String studentCode, String notificationStatus, String notificationError) {}
