package com.eduhub.edu_hub.people.api.dto;

public record LecturerNotificationResponse(Long lecturerId, String lecturerCode,
        String notificationStatus, String notificationError) {}
