package com.eduhub.edu_hub.scheduling.infrastructure.solver;

import static com.eduhub.edu_hub.scheduling.infrastructure.solver.TimetableSolution.*;

import com.eduhub.edu_hub.scheduling.domain.model.TimetableAllocation.SessionPlacement;
import com.eduhub.edu_hub.scheduling.domain.model.TimetableProblem;
import com.eduhub.edu_hub.scheduling.domain.policy.TimetablePolicy;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.NoArgsConstructor;

/** Khởi tạo lựa chọn đủ biến; bộ giải vẫn được đổi từng buổi, phòng, giảng viên và đợt. */
@NoArgsConstructor
public final class TimetableSolutionFactory {

    public static TimetableSolution create(TimetableProblem problem) {
        var solution = new TimetableSolution();
        solution.setProblem(problem);
        List<Long> subjectIds = problem.classes().stream()
                .map(TimetableProblem.ClassRequirement::subjectId)
                .distinct()
                .sorted()
                .toList();
        // Chốt phân đợt và ranh giới trước khi khởi tạo ngày tương đối của từng buổi.
        var subjects = createSubjectAssignments(problem, subjectIds);
        rebalanceSubjectPhases(subjects, subjectIds.size());
        solution.setSubjects(subjects);
        solution.setBoundary(createPhaseBoundary(problem, subjects));
        solution.setClasses(new ArrayList<>());
        solution.setSessions(new ArrayList<>());
        var resourceUsage = new InitialResourceUsage(new HashMap<>(), new HashSet<>(), new HashSet<>());
        for (var requirement : problem.classes()) initializeClass(problem, solution, requirement, resourceUsage);
        return solution;
    }

    private static List<SubjectAssignment> createSubjectAssignments(TimetableProblem problem, List<Long> subjectIds) {
        List<SubjectAssignment> subjects = new ArrayList<>();
        for (int index = 0; index < subjectIds.size(); index++) {
            Long subjectId = subjectIds.get(index);
            boolean pinned = problem.classes().stream()
                    .anyMatch(requirement -> requirement.subjectId().equals(subjectId)
                            && problem.pinnedModuleClassIds().contains(requirement.moduleClassId()));
            int phase = pinned
                    ? problem.previousAllocation().subjects().stream()
                            .filter(subject -> subject.subjectId().equals(subjectId))
                            .findFirst()
                            .orElseThrow()
                            .phase()
                    : index < (subjectIds.size() + 1) / 2 ? 1 : 2;
            subjects.add(new SubjectAssignment(
                    subjectId, subjectIds.size() == 1 ? List.of(1) : List.of(1, 2), phase, pinned));
        }

        return subjects;
    }

    private static void rebalanceSubjectPhases(List<SubjectAssignment> subjects, int subjectCount) {
        // Bù lại số môn từng đợt khi các môn giữ cố định đã thay thứ tự lựa chọn ban đầu.
        long phaseOneCount =
                subjects.stream().filter(subject -> subject.getPhase() == 1).count();
        int targetPhaseOneCount = (subjectCount + 1) / 2;
        for (var subject : subjects) {
            if (phaseOneCount == targetPhaseOneCount) break;
            if (!subject.isPinned() && subject.getPhase() == (phaseOneCount > targetPhaseOneCount ? 1 : 2)) {
                subject.setPhase(phaseOneCount > targetPhaseOneCount ? 2 : 1);
                phaseOneCount += subject.getPhase() == 1 ? 1 : -1;
            }
        }
    }

    private static PhaseBoundary createPhaseBoundary(TimetableProblem problem, List<SubjectAssignment> subjects) {
        int initialWeeks = problem.classes().stream()
                .filter(requirement -> subjects.stream()
                        .anyMatch(subject ->
                                subject.getSubjectId().equals(requirement.subjectId()) && subject.getPhase() == 1))
                .mapToInt(requirement -> (TimetablePolicy.requiredSessions(requirement.totalTeachingPeriods()) + 2) / 3)
                .max()
                .orElse(1);
        LocalDate firstMonday = TimetablePolicy.firstTeachingMonday(problem.startDate());
        int maxWeeks = Math.max(
                1, (int) ChronoUnit.WEEKS.between(firstMonday, problem.endDate()) - TimetablePolicy.EXAM_BUFFER_WEEKS);
        List<Integer> allowedWeeks =
                java.util.stream.IntStream.rangeClosed(1, maxWeeks).boxed().toList();
        boolean hasPins = !problem.pinnedModuleClassIds().isEmpty();
        if (hasPins && problem.previousAllocation().phaseTwoStart() != null)
            initialWeeks = (int) ChronoUnit.WEEKS.between(
                            firstMonday, problem.previousAllocation().phaseTwoStart())
                    - TimetablePolicy.EXAM_BUFFER_WEEKS;
        return new PhaseBoundary(problem.semesterId(), allowedWeeks, Math.min(initialWeeks, maxWeeks), hasPins);
    }

    private static void initializeClass(
            TimetableProblem problem,
            TimetableSolution solution,
            TimetableProblem.ClassRequirement requirement,
            InitialResourceUsage resourceUsage) {
        boolean pinned = problem.pinnedModuleClassIds().contains(requirement.moduleClassId());
        var originalSessions = pinned
                ? problem.previousAllocation().sessions().stream()
                        .filter(session -> session.moduleClassId().equals(requirement.moduleClassId()))
                        .toList()
                : List.<SessionPlacement>of();
        var classAssignment =
                createClassAssignment(problem, requirement, originalSessions, pinned, resourceUsage.lecturerLoad());
        solution.getClasses().add(classAssignment);
        initializeClassSessions(problem, solution, requirement, classAssignment, originalSessions, resourceUsage);
    }

    private static ClassAssignment createClassAssignment(
            TimetableProblem problem,
            TimetableProblem.ClassRequirement requirement,
            List<SessionPlacement> originalSessions,
            boolean pinned,
            Map<Long, Integer> lecturerLoad) {
        List<Long> lecturerIds = problem.lecturers().stream()
                .filter(lecturer -> lecturer.teachingSubjectIds().contains(requirement.subjectId()))
                .map(TimetableProblem.LecturerAvailability::lecturerId)
                .sorted()
                .toList();
        Long lecturerId = pinned
                ? originalSessions.getFirst().lecturerId()
                : lecturerIds.stream()
                        .min(Comparator.comparingInt(candidate -> lecturerLoad.getOrDefault(candidate, 0)))
                        .orElseThrow();
        lecturerLoad.merge(
                lecturerId, TimetablePolicy.requiredSessions(requirement.totalTeachingPeriods()), Integer::sum);
        return new ClassAssignment(
                requirement.moduleClassId(), requirement.subjectId(), lecturerIds, lecturerId, pinned);
    }

    private static void initializeClassSessions(
            TimetableProblem problem,
            TimetableSolution solution,
            TimetableProblem.ClassRequirement requirement,
            ClassAssignment classAssignment,
            List<SessionPlacement> originalSessions,
            InitialResourceUsage resourceUsage) {
        List<Long> roomIds = problem.rooms().stream()
                .filter(room -> room.capacity() >= requirement.maxCapacity())
                .map(TimetableProblem.RoomAvailability::roomId)
                .toList();
        int count = TimetablePolicy.requiredSessions(requirement.totalTeachingPeriods());
        LocalDate firstMonday = TimetablePolicy.firstTeachingMonday(problem.startDate());
        // Tối đa ceil(số ca/2) tuần khi mỗi tuần giữa môn ít nhất hai ca; tuần cuối có thể một ca.
        int maximumClassWeeks = (count + 1) / 2;
        maximumClassWeeks =
                Math.min(maximumClassWeeks, (int) ChronoUnit.WEEKS.between(firstMonday, problem.endDate()) + 1);
        List<RelativeSlot> slots = createAllowedSessionSlots(maximumClassWeeks);
        int initialSessionsPerWeek = count > maximumClassWeeks * 3 ? 4 : 3;
        int[] initialDays = initialSessionsPerWeek == 4 ? new int[] {0, 1, 3, 5} : new int[] {0, 2, 4};
        int phase = solution.getSubjects().stream()
                .filter(subject -> subject.getSubjectId().equals(requirement.subjectId()))
                .findFirst()
                .orElseThrow()
                .getPhase();
        LocalDate phaseStart = phase == 1
                ? firstMonday
                : firstMonday.plusWeeks(solution.getBoundary().getPhaseOneWeeks() + TimetablePolicy.EXAM_BUFFER_WEEKS);
        for (int index = 0; index < count; index++) {
            RelativeSlot initialSlot =
                    new RelativeSlot(index / initialSessionsPerWeek, initialDays[index % initialSessionsPerWeek], 1);
            var placement = classAssignment.isPinned()
                    ? restorePinnedPlacement(originalSessions, index, phaseStart)
                    : selectInitialPlacement(
                            problem, phaseStart, initialSlot, roomIds, classAssignment.getLecturerId(), resourceUsage);
            recordInitialSession(
                    solution,
                    requirement,
                    classAssignment,
                    index,
                    slots,
                    roomIds,
                    phaseStart,
                    placement,
                    resourceUsage);
        }
    }

    private static InitialPlacement restorePinnedPlacement(
            List<SessionPlacement> originalSessions, int index, LocalDate phaseStart) {
        final int sessionIndex = index;
        var original = originalSessions.stream()
                .filter(session -> session.sessionIndex() == sessionIndex)
                .findFirst()
                .orElseThrow();
        long days = ChronoUnit.DAYS.between(phaseStart, original.date());
        var selectedSlot = new RelativeSlot((int) (days / 7), (int) (days % 7), original.shift());
        var selectedRoom = original.roomId();
        return new InitialPlacement(selectedSlot, selectedRoom);
    }

    private static InitialPlacement selectInitialPlacement(
            TimetableProblem problem,
            LocalDate phaseStart,
            RelativeSlot initialSlot,
            List<Long> roomIds,
            Long lecturerId,
            InitialResourceUsage resourceUsage) {
        var selectedSlot = initialSlot;
        var selectedRoom = roomIds.getFirst();
        // Giữ ba ngày giãn nhau để khởi tạo; chọn ca/phòng tránh tài nguyên đã dùng khi có thể.
        LocalDate date = phaseStart.plusWeeks(selectedSlot.weekOffset()).plusDays(selectedSlot.dayOffset());
        boolean selected = false;
        for (int shift = 1; shift <= 4 && !selected; shift++) {
            for (Long roomId : roomIds) {
                final int candidateShift = shift;
                boolean existingConflict = problem.existingSessions().stream()
                        .anyMatch(existing -> TimetablePolicy.occursOn(existing, date)
                                && existing.lessonOrderStart() <= candidateShift * 3
                                && existing.lessonOrderEnd() >= (candidateShift - 1) * 3 + 1
                                && (Objects.equals(existing.roomId(), roomId)
                                        || Objects.equals(existing.lecturerId(), lecturerId)));
                if (!existingConflict
                        && !resourceUsage.occupiedRooms().contains(roomId + ":" + date + ":" + shift)
                        && !resourceUsage.occupiedLecturers().contains(lecturerId + ":" + date + ":" + shift)) {
                    selectedSlot = new RelativeSlot(selectedSlot.weekOffset(), selectedSlot.dayOffset(), shift);
                    selectedRoom = roomId;
                    selected = true;
                    break;
                }
            }
        }
        return new InitialPlacement(selectedSlot, selectedRoom);
    }

    private static void recordInitialSession(
            TimetableSolution solution,
            TimetableProblem.ClassRequirement requirement,
            ClassAssignment classAssignment,
            int index,
            List<RelativeSlot> slots,
            List<Long> roomIds,
            LocalDate phaseStart,
            InitialPlacement placement,
            InitialResourceUsage resourceUsage) {
        var selectedSlot = placement.slot();
        var selectedRoom = placement.roomId();
        LocalDate selectedDate = phaseStart.plusWeeks(selectedSlot.weekOffset()).plusDays(selectedSlot.dayOffset());
        resourceUsage.occupiedRooms().add(selectedRoom + ":" + selectedDate + ":" + selectedSlot.shift());
        resourceUsage
                .occupiedLecturers()
                .add(classAssignment.getLecturerId() + ":" + selectedDate + ":" + selectedSlot.shift());
        solution.getSessions()
                .add(new SessionAssignment(
                        requirement.moduleClassId() + ":" + index,
                        requirement.moduleClassId(),
                        index,
                        slots,
                        roomIds,
                        selectedSlot,
                        selectedRoom,
                        classAssignment.isPinned()));
    }

    private record InitialResourceUsage(
            Map<Long, Integer> lecturerLoad, Set<String> occupiedRooms, Set<String> occupiedLecturers) {}

    private record InitialPlacement(RelativeSlot slot, Long roomId) {}

    private static List<RelativeSlot> createAllowedSessionSlots(int maximumClassWeeks) {
        List<RelativeSlot> slots = new ArrayList<>();
        for (int week = 0; week < maximumClassWeeks; week++)
            for (int day = 0; day < 6; day++)
                for (int shift = 1; shift <= 4; shift++) slots.add(new RelativeSlot(week, day, shift));
        slots = List.copyOf(slots);
        return slots;
    }
}
