package com.eduhub.edu_hub.scheduling.domain.model;

import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Bản chụp bài toán độc lập JPA; không chứa dữ liệu hoặc phân bổ sinh viên. */
public record TimetableProblem(
        Long semesterId,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate registrationStart,
        LocalDate registrationEnd,
        List<ClassRequirement> classes,
        List<LecturerAvailability> lecturers,
        List<RoomAvailability> rooms,
        List<ExistingSession> existingSessions,
        List<ExistingSession> replacedSessions,
        Set<Long> pinnedModuleClassIds,
        TimetableAllocation previousAllocation) {

    public record ClassRequirement(
            Long moduleClassId,
            String moduleClassCode,
            Long subjectId,
            String subjectCode,
            int totalTeachingPeriods,
            int maxCapacity,
            int currentSize,
            ModuleClassStatus status,
            Long lecturerId) {}

    public record LecturerAvailability(Long lecturerId, String fullName, Set<Long> teachingSubjectIds) {}

    public record RoomAvailability(Long roomId, String roomCode, int capacity) {}

    /** Giữ cả lịch lặp cũ; giảng viên lịch sử có thể thiếu hoặc không còn hoạt động. */
    public record ExistingSession(
            Long scheduleId,
            Long moduleClassId,
            Long lecturerId,
            Long roomId,
            int dayOfWeek,
            int lessonOrderStart,
            int lessonOrderEnd,
            LocalDate startDate,
            LocalDate endDate) {}
}
