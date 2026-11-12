package com.eduhub.edu_hub.scheduling.api.mapper;

import com.eduhub.edu_hub.scheduling.api.dto.response.*;
import com.eduhub.edu_hub.scheduling.domain.enums.TeachingShift;
import com.eduhub.edu_hub.scheduling.domain.model.*;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class TimetablePlanResponseMapper {
    private TimetablePlanResponseMapper() {}

    public static TimetablePlanResponse toResponse(
            TimetablePlan plan, TimetableProblem problem, TimetableAllocation allocation, List<String> diagnostics) {
        return new TimetablePlanResponse(
                plan.getId(),
                plan.getSemesterId(),
                plan.getRevision(),
                plan.getState(),
                plan.getHardPenalty(),
                plan.getSoftPenalty(),
                diagnostics,
                toAllocationResponse(problem, allocation),
                plan.getCreatedAt(),
                plan.getUpdatedAt(),
                plan.getState().allowsApplying() && Long.valueOf(0).equals(plan.getHardPenalty()));
    }

    public static TimetableAllocationResponse toAllocationResponse(
            TimetableProblem problem, TimetableAllocation allocation) {
        if (allocation == null) return null;
        var classesById = problem.classes().stream()
                .collect(Collectors.toMap(TimetableProblem.ClassRequirement::moduleClassId, Function.identity()));
        var roomsById = problem.rooms().stream()
                .collect(Collectors.toMap(TimetableProblem.RoomAvailability::roomId, Function.identity()));
        var lecturersById = problem.lecturers().stream()
                .collect(Collectors.toMap(TimetableProblem.LecturerAvailability::lecturerId, Function.identity()));
        var subjectCodesById = new HashMap<Long, String>();
        problem.classes()
                .forEach(requirement -> subjectCodesById.put(requirement.subjectId(), requirement.subjectCode()));
        var subjects = allocation.subjects().stream()
                .map(subject -> new TimetableAllocationResponse.SubjectPhaseResponse(
                        subject.subjectId(), subjectCodesById.get(subject.subjectId()), subject.phase()))
                .toList();
        var sessions = allocation.sessions().stream()
                .map(session -> {
                    var teachingShift =
                            TeachingShift.findByShiftNumber(session.shift()).orElse(null);
                    return new TimetableAllocationResponse.SessionResponse(
                            session.moduleClassId(),
                            classesById.get(session.moduleClassId()).moduleClassCode(),
                            session.sessionIndex(),
                            session.lecturerId(),
                            session.lecturerId() == null || !lecturersById.containsKey(session.lecturerId())
                                    ? null
                                    : lecturersById.get(session.lecturerId()).fullName(),
                            session.roomId(),
                            session.roomId() == null || !roomsById.containsKey(session.roomId())
                                    ? null
                                    : roomsById.get(session.roomId()).roomCode(),
                            session.date(),
                            session.shift(),
                            (session.shift() - 1) * 3 + 1,
                            session.shift() * 3,
                            teachingShift == null ? null : teachingShift.getStartTime(),
                            teachingShift == null ? null : teachingShift.getEndTime());
                })
                .toList();
        var phaseTwoSubjects = allocation.subjects().stream()
                .filter(subject -> subject.phase() == 2)
                .map(TimetableAllocation.SubjectPhase::subjectId)
                .collect(Collectors.toSet());
        LocalDate phaseTwoEnd = allocation.sessions().stream()
                .filter(session -> phaseTwoSubjects.contains(
                        classesById.get(session.moduleClassId()).subjectId()))
                .map(TimetableAllocation.SessionPlacement::date)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);
        return new TimetableAllocationResponse(
                allocation.phaseOneStart(),
                allocation.phaseOneEnd(),
                allocation.phaseTwoStart(),
                phaseTwoEnd,
                subjects,
                sessions);
    }
}
