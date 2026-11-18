package com.eduhub.edu_hub.scheduling.api.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record WeeklyTimetableResponse(
        SemesterResponse semester,
        LocalDate weekStartDate,
        LocalDate weekEndDate,
        List<WeeklySessionResponse> sessions,
        List<ModuleClassSimpleResponse> unscheduledClasses) {
    public record WeeklySessionResponse(
            Long scheduleId,
            Long moduleClassId,
            String moduleClassCode,
            Long subjectId,
            String subjectCode,
            String subjectName,
            Long lecturerId,
            String lecturerName,
            LocalDate date,
            int dayOfWeek,
            int lessonOrderStart,
            int lessonOrderEnd,
            Integer shift,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime,
            Long roomId,
            String roomCode,
            String buildingCode,
            String roomNumber) {}
}
