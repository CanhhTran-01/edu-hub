package com.eduhub.edu_hub.scheduling.application.service;

import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.enrollment.api.dto.response.StudentLearningHistoryResponse;
import com.eduhub.edu_hub.enrollment.application.EnrollmentFacade;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.identity.domain.enums.AccessTokenPurpose;
import com.eduhub.edu_hub.people.api.dto.response.LecturerResponse;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.scheduling.api.dto.request.WeeklyTimetableRequest;
import com.eduhub.edu_hub.scheduling.api.dto.response.*;
import com.eduhub.edu_hub.scheduling.api.dto.response.WeeklyTimetableResponse.WeeklySessionResponse;
import com.eduhub.edu_hub.scheduling.api.mapper.ModuleClassResponseMapper;
import com.eduhub.edu_hub.scheduling.api.mapper.SemesterResponseMapper;
import com.eduhub.edu_hub.scheduling.application.exception.TimetableException;
import com.eduhub.edu_hub.scheduling.application.specification.ModuleClassSpecification;
import com.eduhub.edu_hub.scheduling.domain.model.*;
import com.eduhub.edu_hub.scheduling.domain.model.TimetableProblem.ExistingSession;
import com.eduhub.edu_hub.scheduling.domain.policy.TimetablePolicy;
import com.eduhub.edu_hub.scheduling.domain.repository.*;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class PersonalTimetableQueryService {
    private final IdentityFacade identityFacade;
    private final PeopleFacade.Enrollment studentFacade;
    private final PeopleFacade.Scheduling lecturerFacade;
    private final EnrollmentFacade.StudentRecords studentRecords;
    private final CurriculumFacade.ClassOpening curriculumFacade;
    private final SemesterRepository semesterRepository;
    private final ModuleClassRepository moduleClassRepository;
    private final ClassScheduleQueryService classScheduleQueryService;
    private final Clock clock;

    public WeeklyTimetableResponse findPersonalTimetableForStudent(
            Jwt principal, Long semesterId, WeeklyTimetableRequest request) {
        Long userId = requireCurrentUser(principal);
        var semester = requireSemesterAndWeek(semesterId, request);
        var student = studentFacade.findStudentByUserId(userId);
        var enrolledClassIds = studentRecords.findLearningHistory(student.studentId()).enrollments().stream()
                .filter(enrollment -> enrollment.semesterId().equals(semesterId))
                .map(StudentLearningHistoryResponse.LearningRecordResponse::moduleClassId)
                .collect(Collectors.toSet());
        return loadTimetable(
                semester,
                request.getWeekStartDate(),
                ModuleClassSpecification.matchesStudentTimetable(semesterId, enrolledClassIds));
    }

    public WeeklyTimetableResponse findPersonalTimetableForLecturer(
            Jwt principal, Long semesterId, WeeklyTimetableRequest request) {
        Long userId = requireCurrentUser(principal);
        var semester = requireSemesterAndWeek(semesterId, request);
        Long lecturerId = lecturerFacade
                .findLecturerIdByUserId(userId)
                .orElseThrow(() -> new TimetableException(ErrorCode.LECTURER_NOT_FOUND));
        return loadTimetable(
                semester,
                request.getWeekStartDate(),
                ModuleClassSpecification.matchesRosterCriteria(semesterId, lecturerId, null));
    }

    public WeeklyTimetableResponse findSemesterTimetable(
            Jwt principal, Long semesterId, WeeklyTimetableRequest request) {
        requireCurrentUser(principal);
        var semester = requireSemesterAndWeek(semesterId, request);
        return loadTimetable(
                semester,
                request.getWeekStartDate(),
                ModuleClassSpecification.matchesRosterCriteria(semesterId, null, null));
    }

    private WeeklyTimetableResponse loadTimetable(
            Semester semester, LocalDate weekStartDate, Specification<ModuleClass> scope) {
        // Phạm vi được áp dụng ngay tại query; tập đăng ký rỗng không được biến thành toàn kỳ.
        var classes = moduleClassRepository.findAll(scope, Sort.by("id"));
        var subjectsById = classes.isEmpty()
                ? Map.<Long, SubjectSimpleResponse>of()
                : curriculumFacade
                        .findSubjectsByIds(classes.stream()
                                .map(moduleClass -> moduleClass.getSubject().getId())
                                .collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.toMap(SubjectSimpleResponse::getId, subject -> subject));
        var lecturerIds = classes.stream()
                .filter(moduleClass -> moduleClass.getLecturer() != null)
                .map(moduleClass -> moduleClass.getLecturer().getId())
                .collect(Collectors.toSet());
        var lecturerNamesById = lecturerIds.isEmpty()
                ? Map.<Long, String>of()
                : lecturerFacade.findLecturersByIds(lecturerIds).stream()
                        .collect(Collectors.toMap(LecturerResponse::getId, LecturerResponse::getFullName));
        var schedulesByClassId = classScheduleQueryService.findSchedulesByClassIds(
                classes.stream().map(ModuleClass::getId).toList());
        var sessions = new ArrayList<WeeklySessionResponse>();
        var unscheduledClasses = new ArrayList<ModuleClassSimpleResponse>();
        LocalDate weekEndDate = weekStartDate.plusDays(6);
        LocalDate fromDate = weekStartDate.isBefore(semester.getStartDate()) ? semester.getStartDate() : weekStartDate;
        LocalDate toDate = weekEndDate.isAfter(semester.getEndDate()) ? semester.getEndDate() : weekEndDate;
        for (var moduleClass : classes) {
            var schedules = schedulesByClassId.getOrDefault(moduleClass.getId(), List.of());
            // Xét lịch toàn kỳ trước khi lọc tuần: tuần nghỉ không làm lớp trở thành chưa xếp lịch.
            if (schedules.isEmpty())
                unscheduledClasses.add(
                        ModuleClassResponseMapper.toSimpleResponse(moduleClass, subjectsById, lecturerNamesById));
            var subject = subjectsById.get(moduleClass.getSubject().getId());
            Long lecturerId = moduleClass.getLecturer() == null
                    ? null
                    : moduleClass.getLecturer().getId();
            for (var schedule : schedules) {
                var recurringSession = new ExistingSession(
                        schedule.getId(),
                        moduleClass.getId(),
                        lecturerId,
                        schedule.getRoomId(),
                        schedule.getDayOfWeek(),
                        schedule.getLessonOrderStart(),
                        schedule.getLessonOrderEnd(),
                        schedule.getStartDate(),
                        schedule.getEndDate());
                for (var date : TimetablePolicy.occurrenceDatesBetween(recurringSession, fromDate, toDate)) {
                    sessions.add(new WeeklySessionResponse(
                            schedule.getId(),
                            moduleClass.getId(),
                            moduleClass.getModuleClassCode(),
                            moduleClass.getSubject().getId(),
                            subject == null ? null : subject.getSubjectCode(),
                            subject == null ? null : subject.getSubjectName(),
                            lecturerId,
                            lecturerId == null ? null : lecturerNamesById.get(lecturerId),
                            date,
                            schedule.getDayOfWeek(),
                            schedule.getLessonOrderStart(),
                            schedule.getLessonOrderEnd(),
                            schedule.getShift(),
                            schedule.getStartTime(),
                            schedule.getEndTime(),
                            schedule.getRoomId(),
                            schedule.getRoomCode(),
                            schedule.getBuildingCode(),
                            schedule.getRoomNumber()));
                }
            }
        }
        sessions.sort(Comparator.comparing(WeeklySessionResponse::date)
                .thenComparingInt(WeeklySessionResponse::lessonOrderStart)
                .thenComparing(WeeklySessionResponse::moduleClassId)
                .thenComparing(WeeklySessionResponse::scheduleId));
        return new WeeklyTimetableResponse(
                SemesterResponseMapper.toResponse(semester, LocalDate.now(clock)),
                weekStartDate,
                weekEndDate,
                List.copyOf(sessions),
                List.copyOf(unscheduledClasses));
    }

    private Long requireCurrentUser(Jwt principal) {
        if (principal == null) throw new TimetableException(ErrorCode.UNAUTHENTICATED);
        if (!AccessTokenPurpose.ACCESS.name().equals(principal.getClaim(AccessTokenPurpose.CLAIM_NAME)))
            throw new TimetableException(ErrorCode.FIRST_LOGIN_REQUIRED);
        // TODO(AUTHORIZATION): Quyền gọi các đường cá nhân/toàn kỳ sẽ được áp dụng khi triển khai phân quyền role.
        return identityFacade.requireCurrentUser(principal).getId();
    }

    private Semester requireSemesterAndWeek(Long semesterId, WeeklyTimetableRequest request) {
        if (semesterId == null
                || semesterId <= 0
                || request == null
                || request.getWeekStartDate() == null
                || request.getWeekStartDate().getDayOfWeek() != DayOfWeek.MONDAY
                || request.getWeekStartDate().getYear() < 1
                || request.getWeekStartDate().getYear() > 9999)
            throw new TimetableException(ErrorCode.VALIDATION_ERROR);
        var semester = semesterRepository
                .findByIdAndDeletedFalse(semesterId)
                .orElseThrow(() -> new TimetableException(ErrorCode.SEMESTER_NOT_FOUND));
        if (request.getWeekStartDate().isAfter(semester.getEndDate())
                || request.getWeekStartDate().plusDays(6).isBefore(semester.getStartDate()))
            throw new TimetableException(ErrorCode.VALIDATION_ERROR);
        return semester;
    }
}
