package com.eduhub.edu_hub.scheduling.api.dto.response;

import java.time.LocalDate;

public record ClassScheduleConflictResponse(
        Long moduleClassId, String moduleClassCode, LocalDate date, int lessonOrderStart, int lessonOrderEnd) {}
