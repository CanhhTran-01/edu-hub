package com.eduhub.edu_hub.scheduling.application.service;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.scheduling.application.exception.TimetableException;
import com.eduhub.edu_hub.scheduling.application.specification.ClassScheduleSpecification;
import com.eduhub.edu_hub.scheduling.domain.enums.RoomStatus;
import com.eduhub.edu_hub.scheduling.domain.enums.TimetablePlanState;
import com.eduhub.edu_hub.scheduling.domain.model.*;
import com.eduhub.edu_hub.scheduling.domain.policy.TimetablePolicy;
import com.eduhub.edu_hub.scheduling.domain.repository.*;
import com.eduhub.edu_hub.scheduling.infrastructure.config.TimetablingSettings;
import com.eduhub.edu_hub.scheduling.infrastructure.persistence.TimetablePlanCodec;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TimetableSnapshotService {
    private final CurriculumFacade.ClassOpening curriculumFacade;
    private final PeopleFacade.Scheduling peopleFacade;
    private final RoomRepository roomRepository;
    private final ClassScheduleRepository scheduleRepository;
    private final TimetablePlanRepository planRepository;
    private final TimetablePlanCodec codec;
    private final TimetablingSettings settings;

    @Transactional(propagation = Propagation.MANDATORY)
    public TimetableProblem loadProblem(Semester semester, List<ModuleClass> classes, Set<Long> pinnedClassIds) {
        Set<Long> subjectIds = classes.stream()
                .map(moduleClass -> moduleClass.getSubject().getId())
                .collect(Collectors.toCollection(TreeSet::new));
        var subjectsById = curriculumFacade.requireOpenableSubjects(subjectIds).stream()
                .collect(Collectors.toMap(
                        com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse::getId,
                        Function.identity()));
        var lecturers = peopleFacade.findEligibleLecturers(subjectIds).stream()
                .sorted(Comparator.comparing(
                        com.eduhub.edu_hub.people.api.dto.response.TimetableLecturerResponse::lecturerId))
                .map(lecturer -> new TimetableProblem.LecturerAvailability(
                        lecturer.lecturerId(), lecturer.fullName(), new TreeSet<>(lecturer.teachingSubjectIds())))
                .toList();
        var rooms = roomRepository.findAllByDeletedFalseAndStatusOrderByIdAsc(RoomStatus.ACTIVE).stream()
                .map(room ->
                        new TimetableProblem.RoomAvailability(room.getId(), room.getRoomCode(), room.getCapacity()))
                .toList();
        List<FieldErrorDetail> errors = new ArrayList<>();
        List<TimetableProblem.ClassRequirement> requirements = new ArrayList<>();
        long totalSessions = 0;
        for (var moduleClass : classes) {
            Long subjectId = moduleClass.getSubject().getId();
            var subject = subjectsById.get(subjectId);
            if (subject == null
                    || subject.getTotalTeachingPeriods() == null
                    || subject.getTotalTeachingPeriods() <= 0) {
                errors.add(new FieldErrorDetail(
                        "subjects[" + subjectId + "].totalTeachingPeriods", "Cần bổ sung tổng số tiết chính thức"));
                continue;
            }
            totalSessions += TimetablePolicy.requiredSessions(subject.getTotalTeachingPeriods());
            long availableWeeks = Math.max(
                    0,
                    java.time.temporal.ChronoUnit.WEEKS.between(
                                    TimetablePolicy.firstTeachingMonday(semester.getStartDate()), semester.getEndDate())
                            + 1);
            if (TimetablePolicy.requiredSessions(subject.getTotalTeachingPeriods())
                    > availableWeeks * TimetablePolicy.MAX_SESSIONS_PER_WEEK)
                errors.add(new FieldErrorDetail(
                        "subjects[" + subjectId + "]", "Số buổi vượt quỹ tối đa bốn ca mỗi tuần của kỳ"));
            if (lecturers.stream()
                    .noneMatch(lecturer -> lecturer.teachingSubjectIds().contains(subjectId)))
                errors.add(new FieldErrorDetail(
                        "subjects[" + subjectId + "]", "Không có giảng viên hoạt động có thể dạy môn"));
            if (rooms.stream().noneMatch(room -> room.capacity() >= moduleClass.getMaxCapacity()))
                errors.add(new FieldErrorDetail(
                        "moduleClasses[" + moduleClass.getId() + "]", "Không có phòng hoạt động đủ sức chứa"));
            requirements.add(new TimetableProblem.ClassRequirement(
                    moduleClass.getId(),
                    moduleClass.getModuleClassCode(),
                    subjectId,
                    subject.getSubjectCode(),
                    subject.getTotalTeachingPeriods(),
                    moduleClass.getMaxCapacity(),
                    moduleClass.getCurrentSize(),
                    moduleClass.getStatus(),
                    moduleClass.getLecturer() == null
                            ? null
                            : moduleClass.getLecturer().getId()));
        }
        if (totalSessions > settings.getMaximumSessionsPerPlan())
            errors.add(new FieldErrorDetail("moduleClasses", "Số buổi vượt giới hạn tài nguyên một tác vụ"));
        if (TimetablePolicy.firstTeachingMonday(semester.getStartDate()).isAfter(semester.getEndDate()))
            errors.add(new FieldErrorDetail("startDate", "Kỳ không có tuần học bắt đầu từ thứ Hai"));
        if (!errors.isEmpty()) throw new TimetableException(ErrorCode.TIMETABLE_INVALID_INPUT, errors);
        var classIds = classes.stream().map(ModuleClass::getId).collect(Collectors.toCollection(TreeSet::new));
        var replacedSessions =
                scheduleRepository
                        .findAll(ClassScheduleSpecification.notDeletedAndModuleClassIdIn(classIds), Sort.by("id"))
                        .stream()
                        .map(TimetableSnapshotService::toExistingSession)
                        .toList();
        var existingSessions = scheduleRepository
                .findAll(
                        ClassScheduleSpecification.overlappingPeriodExcludingClasses(
                                semester.getStartDate(), semester.getEndDate(), classIds),
                        Sort.by("id"))
                .stream()
                .map(TimetableSnapshotService::toExistingSession)
                .toList();
        var previous = planRepository
                .findFirstBySemesterIdAndStateAndDeletedFalseOrderByAppliedAtDesc(
                        semester.getId(), TimetablePlanState.APPLIED)
                .filter(plan -> plan.getAllocationJson() != null)
                .map(plan -> codec.read(plan.getAllocationJson(), TimetableAllocation.class))
                .orElse(null);
        if (!pinnedClassIds.isEmpty() && (previous == null || !classIds.containsAll(pinnedClassIds)))
            throw new TimetableException(
                    ErrorCode.TIMETABLE_INVALID_INPUT,
                    List.of(new FieldErrorDetail(
                            "pinnedModuleClassIds", "Chỉ giữ cố định lớp có lịch chính thức trong kỳ")));
        for (Long pinnedClassId : pinnedClassIds) {
            var original = previous.sessions().stream()
                    .filter(session -> session.moduleClassId().equals(pinnedClassId))
                    .toList();
            var requirement = requirements.stream()
                    .filter(candidate -> candidate.moduleClassId().equals(pinnedClassId))
                    .findFirst()
                    .orElseThrow();
            if (original.size() != TimetablePolicy.requiredSessions(requirement.totalTeachingPeriods()))
                throw new TimetableException(
                        ErrorCode.TIMETABLE_INVALID_INPUT,
                        List.of(new FieldErrorDetail(
                                "pinnedModuleClassIds", "Lớp giữ cố định chưa có đủ lịch: " + pinnedClassId)));
            var expectedSessions = original.stream()
                    .map(session -> new PinnedSession(
                            session.lecturerId(),
                            session.roomId(),
                            session.date(),
                            (session.shift() - 1) * 3 + 1,
                            session.shift() * 3))
                    .collect(Collectors.toSet());
            var actualSessions = replacedSessions.stream()
                    .filter(session -> session.moduleClassId().equals(pinnedClassId))
                    .flatMap(session -> TimetablePolicy.occurrenceDates(session).stream()
                            .map(date -> new PinnedSession(
                                    session.lecturerId(),
                                    session.roomId(),
                                    date,
                                    session.lessonOrderStart(),
                                    session.lessonOrderEnd())))
                    .toList();
            if (actualSessions.size() != expectedSessions.size()
                    || !expectedSessions.equals(new HashSet<>(actualSessions)))
                throw new TimetableException(
                        ErrorCode.TIMETABLE_INVALID_INPUT,
                        List.of(new FieldErrorDetail(
                                "pinnedModuleClassIds", "Lịch của lớp giữ cố định đã thay đổi: " + pinnedClassId)));
            if (original.stream()
                    .anyMatch(session -> lecturers.stream()
                                    .noneMatch(lecturer -> lecturer.lecturerId().equals(session.lecturerId())
                                            && lecturer.teachingSubjectIds().contains(requirement.subjectId()))
                            || rooms.stream()
                                    .noneMatch(room -> room.roomId().equals(session.roomId())
                                            && room.capacity() >= requirement.maxCapacity())))
                throw new TimetableException(
                        ErrorCode.TIMETABLE_INVALID_INPUT,
                        List.of(new FieldErrorDetail(
                                "pinnedModuleClassIds",
                                "Giảng viên hoặc phòng của lớp giữ cố định không còn phù hợp: " + pinnedClassId)));
        }
        if (!pinnedClassIds.isEmpty()) {
            var pinnedSubjectIds = requirements.stream()
                    .filter(requirement -> pinnedClassIds.contains(requirement.moduleClassId()))
                    .map(TimetableProblem.ClassRequirement::subjectId)
                    .collect(Collectors.toSet());
            long pinnedPhaseOne = previous.subjects().stream()
                    .filter(subject -> pinnedSubjectIds.contains(subject.subjectId()) && subject.phase() == 1)
                    .count();
            long pinnedPhaseTwo = previous.subjects().stream()
                    .filter(subject -> pinnedSubjectIds.contains(subject.subjectId()) && subject.phase() == 2)
                    .count();
            if (pinnedPhaseOne > (subjectIds.size() + 1) / 2 || pinnedPhaseTwo > subjectIds.size() / 2)
                throw new TimetableException(
                        ErrorCode.TIMETABLE_INVALID_INPUT,
                        List.of(new FieldErrorDetail(
                                "pinnedModuleClassIds", "Các môn giữ cố định không còn phù hợp quy tắc chia hai đợt")));
        }
        return new TimetableProblem(
                semester.getId(),
                semester.getStartDate(),
                semester.getEndDate(),
                semester.getRegistrationStart(),
                semester.getRegistrationEnd(),
                requirements,
                lecturers,
                rooms,
                existingSessions,
                replacedSessions,
                new TreeSet<>(pinnedClassIds),
                previous);
    }

    public static TimetableProblem.ExistingSession toExistingSession(ClassSchedule schedule) {
        return new TimetableProblem.ExistingSession(
                schedule.getId(),
                schedule.getModuleClass().getId(),
                schedule.getModuleClass().getLecturer() == null
                        ? null
                        : schedule.getModuleClass().getLecturer().getId(),
                schedule.getRoom().getId(),
                schedule.getDayOfWeek(),
                schedule.getLessonOrderStart(),
                schedule.getLessonOrderEnd(),
                schedule.getStartDate(),
                schedule.getEndDate());
    }

    private record PinnedSession(
            Long lecturerId, Long roomId, java.time.LocalDate date, int lessonOrderStart, int lessonOrderEnd) {}
}
