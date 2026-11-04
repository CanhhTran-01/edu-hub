package com.eduhub.edu_hub.people.api.dto;

import java.util.List;

public record CreateLecturerResponse(LecturerResponse lecturer, List<String> warnings,
        String notificationStatus, String notificationError) {}
