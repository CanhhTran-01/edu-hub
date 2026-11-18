package com.eduhub.edu_hub.scheduling;

import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.scheduling.domain.model.*;
import com.eduhub.edu_hub.scheduling.domain.policy.TimetablePolicy;
import java.time.LocalDate;
import java.util.*;

/** Bộ dữ liệu nhỏ dùng chung cho kiểm thử thuần; không đăng ký nguồn dữ liệu giả ở runtime. */
public final class TimetableTestData {
    private TimetableTestData() {}

    public static TimetableProblem problem(int subjectCount, int classesPerSubject, int teachingPeriods) {
        List<TimetableProblem.ClassRequirement> classes = new ArrayList<>();
        Set<Long> subjectIds = new TreeSet<>();
        for (long subjectId = 1; subjectId <= subjectCount; subjectId++) {
            subjectIds.add(subjectId);
            for (int classIndex = 0; classIndex < classesPerSubject; classIndex++)
                classes.add(new TimetableProblem.ClassRequirement(
                        subjectId * 100 + classIndex,
                        "MC_" + subjectId + "_" + classIndex,
                        subjectId,
                        "CS" + subjectId,
                        teachingPeriods,
                        60,
                        0,
                        ModuleClassStatus.UNSCHEDULED,
                        null));
        }
        return new TimetableProblem(
                7L,
                LocalDate.of(2027, 8, 7),
                LocalDate.of(2027, 12, 31),
                LocalDate.of(2027, 7, 1),
                LocalDate.of(2027, 7, 30),
                classes,
                List.of(
                        new TimetableProblem.LecturerAvailability(201L, "Giảng viên A", subjectIds),
                        new TimetableProblem.LecturerAvailability(202L, "Giảng viên B", subjectIds)),
                List.of(
                        new TimetableProblem.RoomAvailability(501L, "101-TA1", 80),
                        new TimetableProblem.RoomAvailability(502L, "102-TA1", 80)),
                List.of(),
                List.of(),
                Set.of(),
                null);
    }

    public static TimetableAllocation allocation(TimetableProblem problem) {
        LocalDate firstMonday = TimetablePolicy.firstTeachingMonday(problem.startDate());
        int count =
                TimetablePolicy.requiredSessions(problem.classes().getFirst().totalTeachingPeriods());
        int weeks = (count + 2) / 3;
        var subjectIds = problem.classes().stream()
                .map(TimetableProblem.ClassRequirement::subjectId)
                .distinct()
                .sorted()
                .toList();
        List<TimetableAllocation.SubjectPhase> subjects = new ArrayList<>();
        for (int index = 0; index < subjectIds.size(); index++)
            subjects.add(new TimetableAllocation.SubjectPhase(
                    subjectIds.get(index), index < (subjectIds.size() + 1) / 2 ? 1 : 2));
        LocalDate phaseTwoStart = subjectIds.size() > 1 ? firstMonday.plusWeeks(weeks + 2) : null;
        List<TimetableAllocation.SessionPlacement> sessions = new ArrayList<>();
        int classIndex = 0;
        for (var requirement : problem.classes()) {
            int phase = subjects.stream()
                    .filter(subject -> subject.subjectId().equals(requirement.subjectId()))
                    .findFirst()
                    .orElseThrow()
                    .phase();
            LocalDate phaseStart = phase == 1 ? firstMonday : phaseTwoStart;
            for (int index = 0; index < count; index++)
                sessions.add(new TimetableAllocation.SessionPlacement(
                        requirement.moduleClassId(),
                        index,
                        201L,
                        501L,
                        phaseStart.plusWeeks(index / 3).plusDays(index % 3 * 2),
                        classIndex % 4 + 1));
            classIndex++;
        }
        return new TimetableAllocation(
                firstMonday,
                phaseTwoStart == null
                        ? sessions.stream()
                                .map(TimetableAllocation.SessionPlacement::date)
                                .max(LocalDate::compareTo)
                                .orElseThrow()
                        : firstMonday.plusWeeks(weeks).minusDays(1),
                phaseTwoStart,
                subjects,
                sessions);
    }
}
