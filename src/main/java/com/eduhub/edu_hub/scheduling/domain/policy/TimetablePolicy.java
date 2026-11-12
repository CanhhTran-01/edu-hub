package com.eduhub.edu_hub.scheduling.domain.policy;

import com.eduhub.edu_hub.scheduling.domain.model.TimetableAllocation;
import com.eduhub.edu_hub.scheduling.domain.model.TimetableAllocation.SessionPlacement;
import com.eduhub.edu_hub.scheduling.domain.model.TimetableProblem;
import com.eduhub.edu_hub.scheduling.domain.model.TimetableProblem.ExistingSession;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;

/** Quy tắc dùng chung khi chấm điểm và trước khi áp dụng; không truy vấn dữ liệu. */
@NoArgsConstructor
public final class TimetablePolicy {
    public static final int PERIODS_PER_SESSION = 3;
    public static final int MIN_SESSIONS_PER_WEEK = 2;
    public static final int MAX_SESSIONS_PER_WEEK = 4;
    public static final int EXAM_BUFFER_WEEKS = 2;
    private static final int MAX_DIAGNOSTICS = 100;
    private static final int CONSECUTIVE_DAY_PENALTY = 10;
    private static final int SHIFT_CHANGE_PENALTY = 2;

    // TODO(HOLIDAY_CALENDAR): Bổ sung ngày lễ khi có nguồn cấu hình chính thức; hiện chỉ loại Chủ nhật.

    public static int requiredSessions(int totalTeachingPeriods) {
        if (totalTeachingPeriods <= 0) throw new IllegalArgumentException("Tổng số tiết phải dương");
        return (totalTeachingPeriods - 1) / PERIODS_PER_SESSION + 1;
    }

    public static LocalDate firstTeachingMonday(LocalDate startDate) {
        return startDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    public static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static boolean occursOn(ExistingSession session, LocalDate date) {
        return !date.isBefore(session.startDate())
                && !date.isAfter(session.endDate())
                && date.getDayOfWeek().getValue() + 1 == session.dayOfWeek();
    }

    public static List<LocalDate> occurrenceDates(ExistingSession session) {
        if (session.dayOfWeek() < 2
                || session.dayOfWeek() > 8
                || session.endDate().isBefore(session.startDate())) return List.of();
        LocalDate first = session.startDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.of(session.dayOfWeek() - 1)));
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = first; !date.isAfter(session.endDate()); date = date.plusWeeks(1)) dates.add(date);
        return dates;
    }

    public static Assessment assess(TimetableProblem problem, TimetableAllocation allocation, boolean explain) {
        Penalties penalties = new Penalties(explain);
        if (allocation == null) {
            penalties.reject(1, "Chưa có phương án");
            return penalties.assessment();
        }
        var requirementsByClassId = problem.classes().stream()
                .collect(Collectors.toMap(TimetableProblem.ClassRequirement::moduleClassId, Function.identity()));
        var roomsById = problem.rooms().stream()
                .collect(Collectors.toMap(TimetableProblem.RoomAvailability::roomId, Function.identity()));
        var lecturersById = problem.lecturers().stream()
                .collect(Collectors.toMap(TimetableProblem.LecturerAvailability::lecturerId, Function.identity()));
        Set<Long> subjectIds = problem.classes().stream()
                .map(TimetableProblem.ClassRequirement::subjectId)
                .collect(Collectors.toSet());
        Map<Long, Integer> phasesBySubjectId = new HashMap<>();
        for (var subject : allocation.subjects()) {
            if (!subjectIds.contains(subject.subjectId())
                    || (subject.phase() != 1 && subject.phase() != 2)
                    || phasesBySubjectId.putIfAbsent(subject.subjectId(), subject.phase()) != null)
                penalties.reject(1, "Phân đợt môn không hợp lệ: " + subject.subjectId());
        }
        penalties.reject(subjectIds.size() - phasesBySubjectId.size(), "Thiếu phân đợt môn");
        long phaseOneCount =
                phasesBySubjectId.values().stream().filter(phase -> phase == 1).count();
        penalties.reject(Math.abs((subjectIds.size() + 1L) / 2 - phaseOneCount), "Chia số môn hai đợt chưa đúng");
        LocalDate firstMonday = firstTeachingMonday(problem.startDate());
        if (!firstMonday.equals(allocation.phaseOneStart())) penalties.reject(1, "Sai tuần bắt đầu đợt 1");
        boolean hasPhaseTwo = subjectIds.size() > 1;
        if (hasPhaseTwo
                && (allocation.phaseOneEnd() == null
                        || allocation.phaseTwoStart() == null
                        || allocation.phaseTwoStart().getDayOfWeek() != DayOfWeek.MONDAY
                        || !allocation
                                .phaseTwoStart()
                                .equals(allocation.phaseOneEnd().plusDays(15))
                        || allocation.phaseOneEnd().getDayOfWeek() != DayOfWeek.SUNDAY))
            penalties.reject(1, "Hai đợt phải cách nhau hai tuần lịch đầy đủ");
        if (!hasPhaseTwo && allocation.phaseTwoStart() != null) penalties.reject(1, "Một môn chỉ dùng đợt 1");

        Map<Long, List<SessionPlacement>> sessionsByClassId = new HashMap<>();
        Map<ResourceSlot, Integer> roomUsage = new HashMap<>();
        Map<ResourceSlot, Integer> lecturerUsage = new HashMap<>();
        Map<ResourceDay, SortedSet<Integer>> lecturerShiftsByDay = new HashMap<>();
        Map<Long, Long> lecturerSessionCounts = new HashMap<>();
        LocalDate lastPhaseOneDate = null;
        Set<String> sessionKeys = new HashSet<>();
        for (var session : allocation.sessions()) {
            var requirement = requirementsByClassId.get(session.moduleClassId());
            if (requirement == null) {
                penalties.reject(1, "Có lớp ngoài phạm vi: " + session.moduleClassId());
                continue;
            }
            sessionsByClassId
                    .computeIfAbsent(session.moduleClassId(), ignored -> new ArrayList<>())
                    .add(session);
            if (session.sessionIndex() < 0
                    || session.sessionIndex() >= requiredSessions(requirement.totalTeachingPeriods())
                    || !sessionKeys.add(session.moduleClassId() + ":" + session.sessionIndex()))
                penalties.reject(1, "Chỉ số buổi bị thiếu hoặc lặp: " + requirement.moduleClassCode());
            if (session.date() == null || session.roomId() == null || session.lecturerId() == null) {
                penalties.reject(1, "Chưa phân công đủ ngày, phòng hoặc giảng viên: " + requirement.moduleClassCode());
                continue;
            }
            if (session.shift() < 1 || session.shift() > 4 || session.date().getDayOfWeek() == DayOfWeek.SUNDAY)
                penalties.reject(1, "Ngày hoặc ca không hợp lệ: " + requirement.moduleClassCode());
            if (session.date().isBefore(problem.startDate()) || session.date().isAfter(problem.endDate()))
                penalties.reject(1, "Buổi học ngoài ngày kỳ: " + requirement.moduleClassCode());
            Integer phase = phasesBySubjectId.get(requirement.subjectId());
            if (phase == null
                    || (phase == 1
                            && (session.date().isBefore(firstMonday)
                                    || allocation.phaseOneEnd() == null
                                    || session.date().isAfter(allocation.phaseOneEnd())))
                    || (phase == 2
                            && (allocation.phaseTwoStart() == null
                                    || session.date().isBefore(allocation.phaseTwoStart()))))
                penalties.reject(1, "Buổi học ngoài đợt của môn: " + requirement.moduleClassCode());
            if (Integer.valueOf(1).equals(phase)
                    && (lastPhaseOneDate == null || session.date().isAfter(lastPhaseOneDate)))
                lastPhaseOneDate = session.date();
            var room = roomsById.get(session.roomId());
            if (room == null || room.capacity() < requirement.maxCapacity())
                penalties.reject(1, "Phòng không đủ điều kiện: " + requirement.moduleClassCode());
            var lecturer = lecturersById.get(session.lecturerId());
            if (lecturer == null || !lecturer.teachingSubjectIds().contains(requirement.subjectId()))
                penalties.reject(1, "Giảng viên không đủ điều kiện: " + requirement.moduleClassCode());
            penalties.reject(
                    roomUsage.merge(
                                    new ResourceSlot(session.roomId(), session.date(), session.shift()),
                                    1,
                                    Integer::sum)
                            - 1,
                    "Trùng phòng ngày " + session.date());
            penalties.reject(
                    lecturerUsage.merge(
                                    new ResourceSlot(session.lecturerId(), session.date(), session.shift()),
                                    1,
                                    Integer::sum)
                            - 1,
                    "Trùng giảng viên ngày " + session.date());
            lecturerShiftsByDay
                    .computeIfAbsent(new ResourceDay(session.lecturerId(), session.date()), ignored -> new TreeSet<>())
                    .add(session.shift());
            lecturerSessionCounts.merge(session.lecturerId(), 1L, Long::sum);
            for (var existing : problem.existingSessions()) {
                if (occursOn(existing, session.date())
                        && existing.lessonOrderStart() <= session.shift() * 3
                        && existing.lessonOrderEnd() >= (session.shift() - 1) * 3 + 1
                        && (Objects.equals(existing.roomId(), session.roomId())
                                || Objects.equals(existing.lecturerId(), session.lecturerId())))
                    penalties.reject(1, "Xung đột lịch đang có: " + requirement.moduleClassCode());
            }
        }
        if (hasPhaseTwo
                && lastPhaseOneDate != null
                && allocation.phaseOneEnd() != null
                && !weekStart(lastPhaseOneDate).plusDays(6).equals(allocation.phaseOneEnd()))
            penalties.reject(
                    Math.abs(
                            ChronoUnit.WEEKS.between(weekStart(lastPhaseOneDate), weekStart(allocation.phaseOneEnd()))),
                    "Khoảng đệm phải nối sau tuần kết thúc đợt 1");

        for (var requirement : problem.classes()) {
            var classSessions = sessionsByClassId.getOrDefault(requirement.moduleClassId(), List.of());
            penalties.reject(
                    Math.abs(requiredSessions(requirement.totalTeachingPeriods()) - classSessions.size()),
                    "Chưa đủ số buổi: " + requirement.moduleClassCode());
            var datedSessions = classSessions.stream()
                    .filter(session -> session.date() != null)
                    .toList();
            penalties.reject(
                    Math.max(
                            0,
                            classSessions.stream()
                                            .map(SessionPlacement::lecturerId)
                                            .distinct()
                                            .count()
                                    - 1),
                    "Một lớp chỉ có một giảng viên: " + requirement.moduleClassCode());
            if (datedSessions.isEmpty()) continue;
            var byDate = datedSessions.stream().collect(Collectors.groupingBy(SessionPlacement::date));
            byDate.values()
                    .forEach(sessions -> penalties.reject(
                            sessions.size() - 1, "Một lớp chỉ học một ca/ngày: " + requirement.moduleClassCode()));
            List<LocalDate> dates = byDate.keySet().stream().sorted().toList();
            LocalDate firstWeek = weekStart(dates.getFirst());
            LocalDate lastWeek = weekStart(dates.getLast());
            LocalDate expectedStart = Integer.valueOf(1).equals(phasesBySubjectId.get(requirement.subjectId()))
                    ? firstMonday
                    : allocation.phaseTwoStart();
            if (!firstWeek.equals(expectedStart))
                penalties.reject(1, "Lớp không bắt đầu cùng tuần đợt: " + requirement.moduleClassCode());
            var sessionsByWeek = datedSessions.stream()
                    .collect(Collectors.groupingBy(session -> weekStart(session.date()), Collectors.counting()));
            for (LocalDate week = firstWeek; !week.isAfter(lastWeek); week = week.plusWeeks(1)) {
                long count = sessionsByWeek.getOrDefault(week, 0L);
                if (count == 0) penalties.reject(1, "Có tuần nghỉ giữa môn: " + requirement.moduleClassCode());
                else if (count < MIN_SESSIONS_PER_WEEK && !week.equals(lastWeek))
                    penalties.reject(
                            MIN_SESSIONS_PER_WEEK - count, "Thiếu hai ca/tuần: " + requirement.moduleClassCode());
                penalties.reject(count - MAX_SESSIONS_PER_WEEK, "Vượt bốn ca/tuần: " + requirement.moduleClassCode());
            }
            for (int index = 1; index < dates.size(); index++) {
                if (dates.get(index - 1).plusDays(1).equals(dates.get(index)))
                    penalties.soft += CONSECUTIVE_DAY_PENALTY;
            }
            // Ưu tiên ổn định theo thứ qua các tuần nhưng không ép mọi tuần phải dùng một mẫu lịch.
            var sameWeekday = datedSessions.stream()
                    .collect(Collectors.groupingBy(session -> session.date().getDayOfWeek()));
            for (var weekdaySessions : sameWeekday.values()) {
                penalties.soft += Math.max(
                                0,
                                weekdaySessions.stream()
                                                .map(SessionPlacement::shift)
                                                .distinct()
                                                .count()
                                        - 1)
                        * SHIFT_CHANGE_PENALTY;
                penalties.soft += Math.max(
                        0,
                        weekdaySessions.stream()
                                        .map(SessionPlacement::roomId)
                                        .distinct()
                                        .count()
                                - 1);
            }
            if (problem.pinnedModuleClassIds().contains(requirement.moduleClassId())) {
                var previous = problem.previousAllocation();
                Set<SessionPlacement> original = previous == null
                        ? Set.of()
                        : previous.sessions().stream()
                                .filter(session -> session.moduleClassId().equals(requirement.moduleClassId()))
                                .collect(Collectors.toSet());
                if (!original.equals(new HashSet<>(classSessions)))
                    penalties.reject(1, "Lớp giữ cố định đã bị đổi: " + requirement.moduleClassCode());
            }
        }
        // Ca trống giữa hai ca dạy trong cùng ngày là ưu tiên mềm, không đặt định mức dạy cứng.
        for (var shifts : lecturerShiftsByDay.values()) {
            Integer previousShift = null;
            for (int shift : shifts) {
                if (previousShift != null) penalties.soft += Math.max(0, shift - previousShift - 1);
                previousShift = shift;
            }
        }
        // Chuẩn hóa tải bình phương theo số buổi để ưu tiên cân tải trong phương án, không đặt trần giờ dạy.
        long assignedSessions = lecturerSessionCounts.values().stream()
                .mapToLong(Long::longValue)
                .sum();
        if (assignedSessions > 0)
            penalties.soft += lecturerSessionCounts.values().stream()
                            .mapToLong(count -> count * count)
                            .sum()
                    / assignedSessions;
        return penalties.assessment();
    }

    public record Assessment(long hardPenalty, long softPenalty, List<String> violations) {
        public boolean isFeasible() {
            return hardPenalty == 0;
        }
    }

    private record ResourceSlot(Long resourceId, LocalDate date, int shift) {}

    private record ResourceDay(Long resourceId, LocalDate date) {}

    private static final class Penalties {
        private long hard;
        private long soft;
        private final boolean explain;
        private final Set<String> violations = new LinkedHashSet<>();

        private Penalties(boolean explain) {
            this.explain = explain;
        }

        private void reject(long amount, String message) {
            if (amount <= 0) return;
            hard += amount;
            if (explain && violations.size() < MAX_DIAGNOSTICS) violations.add(message);
        }

        private Assessment assessment() {
            return new Assessment(hard, soft, List.copyOf(violations));
        }
    }
}
