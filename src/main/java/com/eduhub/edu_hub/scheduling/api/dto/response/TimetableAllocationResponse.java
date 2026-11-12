package com.eduhub.edu_hub.scheduling.api.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record TimetableAllocationResponse(
        LocalDate phaseOneStart,
        LocalDate phaseOneEnd,
        LocalDate phaseTwoStart,
        LocalDate phaseTwoEnd,
        List<SubjectPhaseResponse> subjects,
        List<SessionResponse> sessions) {

    public record SubjectPhaseResponse(Long subjectId, String subjectCode, int phase) {}

    public record SessionResponse(
            Long moduleClassId,
            String moduleClassCode,
            int sessionIndex,
            Long lecturerId,
            String lecturerName,
            Long roomId,
            String roomCode,
            LocalDate date,
            int shift,
            int lessonOrderStart,
            int lessonOrderEnd,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime) {}
}
