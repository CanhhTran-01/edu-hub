package com.eduhub.edu_hub.people.api.dto.response;

import java.util.List;

public record CreateStudentResponse(
        StudentResponse student, List<String> warnings, String notificationStatus, String notificationError) {}
