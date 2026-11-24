package com.eduhub.edu_hub.scheduling.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.domain.model.Subject;
import com.eduhub.edu_hub.enrollment.api.dto.response.StudentLearningHistoryResponse;
import com.eduhub.edu_hub.enrollment.api.dto.response.StudentLearningHistoryResponse.LearningRecordResponse;
import com.eduhub.edu_hub.enrollment.application.EnrollmentFacade;
import com.eduhub.edu_hub.enrollment.domain.enums.EnrollmentStatus;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.response.EnrollmentStudentResponse;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.scheduling.api.dto.request.WeeklyTimetableRequest;
import com.eduhub.edu_hub.scheduling.api.dto.response.ClassScheduleResponse;
import com.eduhub.edu_hub.scheduling.application.exception.TimetableException;
import com.eduhub.edu_hub.scheduling.domain.enums.ModuleClassStatus;
import com.eduhub.edu_hub.scheduling.domain.model.*;
import com.eduhub.edu_hub.scheduling.domain.repository.*;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.persistence.criteria.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.oauth2.jwt.Jwt;

class PersonalTimetableQueryServiceTest {
    IdentityFacade identityFacade = mock(IdentityFacade.class);
    PeopleFacade.Enrollment studentFacade = mock(PeopleFacade.Enrollment.class);
    PeopleFacade.Scheduling lecturerFacade = mock(PeopleFacade.Scheduling.class);
    EnrollmentFacade.StudentRecords studentRecords = mock(EnrollmentFacade.StudentRecords.class);
    CurriculumFacade.ClassOpening curriculumFacade = mock(CurriculumFacade.ClassOpening.class);
    SemesterRepository semesterRepository = mock(SemesterRepository.class);
    ModuleClassRepository moduleClassRepository = mock(ModuleClassRepository.class);
    ClassScheduleQueryService scheduleQuery = mock(ClassScheduleQueryService.class);
    Clock clock = Clock.fixed(Instant.parse("2026-11-16T23:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    PersonalTimetableQueryService service = new PersonalTimetableQueryService(
            identityFacade,
            studentFacade,
            lecturerFacade,
            studentRecords,
            curriculumFacade,
            semesterRepository,
            moduleClassRepository,
            scheduleQuery,
            clock);
    Jwt principal = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("student")
            .claim("token_use", "ACCESS")
            .build();
    Semester semester = Semester.builder()
            .semesterCode("HK_1_2026_2027")
            .startDate(LocalDate.of(2026, 11, 18))
            .endDate(LocalDate.of(2026, 12, 31))
            .build();

    @BeforeEach
    void authenticate() {
        semester.setId(2L);
        when(identityFacade.requireCurrentUser(principal))
                .thenReturn(UserInfoResponse.builder().id(7L).build());
        when(semesterRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(semester));
    }

    @Test
    void expandsLegacyAndSingleDateSchedulesClipsSemesterAndDoesNotMarkWeekOffAsUnscheduled() {
        var subject = Subject.builder().subjectCode("CS1").build();
        subject.setId(10L);
        var recurringClass = moduleClass(100L, subject);
        var laterClass = moduleClass(101L, subject);
        var unscheduledClass = moduleClass(102L, subject);
        when(moduleClassRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(recurringClass, laterClass, unscheduledClass));
        when(curriculumFacade.findSubjectsByIds(Set.of(10L)))
                .thenReturn(List.of(SubjectSimpleResponse.builder()
                        .id(10L)
                        .subjectCode("CS1")
                        .credits(3)
                        .build()));
        when(scheduleQuery.findSchedulesByClassIds(List.of(100L, 101L, 102L)))
                .thenReturn(Map.of(
                        100L,
                                List.of(
                                        schedule(1L, 2, "2026-11-01", "2026-12-31"),
                                        schedule(2L, 4, "2026-11-01", "2026-12-31"),
                                        schedule(3L, 6, "2026-11-20", "2026-11-20")),
                        101L, List.of(schedule(4L, 2, "2026-11-23", "2026-11-23"))));
        var timetable = service.findSemesterTimetable(principal, 2L, request("2026-11-16"));
        assertThat(timetable.sessions())
                .extracting(session -> session.date())
                .containsExactly(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 20));
        assertThat(timetable.unscheduledClasses())
                .extracting(summary -> summary.getId())
                .containsExactly(102L);
        assertThat(timetable.sessions()).allMatch(session -> session.lecturerName() == null);
        verify(moduleClassRepository, never()).save(any());
    }

    @Test
    void resolvesStudentFromUserAndAppliesRegisteredClassIdsOfRequestedTermInsideQuery() {
        when(studentFacade.findStudentByUserId(7L))
                .thenReturn(new EnrollmentStudentResponse(99L, "SV99", "Sinh viên", null, null, null, null, null));
        var current = enrollment(100L, 2L);
        var otherTerm = enrollment(999L, 3L);
        when(studentRecords.findLearningHistory(99L))
                .thenReturn(new StudentLearningHistoryResponse(
                        false, 2026, "Phạm vi", Set.of(), List.of(current, otherTerm)));
        service.findPersonalTimetableForStudent(principal, 2L, request("2026-11-16"));
        ArgumentCaptor<Specification<ModuleClass>> scope = ArgumentCaptor.forClass(Specification.class);
        verify(moduleClassRepository).findAll(scope.capture(), any(Sort.class));
        Root<ModuleClass> root = mock(Root.class, RETURNS_DEEP_STUBS);
        var builder = mock(CriteriaBuilder.class);
        scope.getValue().toPredicate(root, mock(CriteriaQuery.class), builder);
        verify(root.get("id")).in(Set.of(100L));
        verify(builder).equal(root.get("semester").get("id"), 2L);
        verify(studentRecords).findLearningHistory(99L);
    }

    @Test
    void emptyStudentHistoryProducesFalseScopeInsteadOfAllClasses() {
        when(studentFacade.findStudentByUserId(7L))
                .thenReturn(new EnrollmentStudentResponse(99L, "SV99", "Sinh viên", null, null, null, null, null));
        when(studentRecords.findLearningHistory(99L))
                .thenReturn(new StudentLearningHistoryResponse(true, 2026, "Phạm vi", Set.of(), List.of()));
        var timetable = service.findPersonalTimetableForStudent(principal, 2L, request("2026-11-16"));
        assertThat(timetable.sessions()).isEmpty();
        ArgumentCaptor<Specification<ModuleClass>> scope = ArgumentCaptor.forClass(Specification.class);
        verify(moduleClassRepository).findAll(scope.capture(), any(Sort.class));
        var builder = mock(CriteriaBuilder.class);
        scope.getValue().toPredicate(mock(Root.class, RETURNS_DEEP_STUBS), mock(CriteriaQuery.class), builder);
        verify(builder).disjunction();
    }

    @Test
    void lecturerIdentityIsNotUserIdAndMissingProfileNeverFallsBackToWholeTerm() {
        when(lecturerFacade.findLecturerIdByUserId(7L)).thenReturn(Optional.of(201L));
        service.findPersonalTimetableForLecturer(principal, 2L, request("2026-11-16"));
        ArgumentCaptor<Specification<ModuleClass>> scope = ArgumentCaptor.forClass(Specification.class);
        verify(moduleClassRepository).findAll(scope.capture(), any(Sort.class));
        Root<ModuleClass> root = mock(Root.class, RETURNS_DEEP_STUBS);
        var builder = mock(CriteriaBuilder.class);
        scope.getValue().toPredicate(root, mock(CriteriaQuery.class), builder);
        verify(builder).equal(root.get("lecturerId"), 201L);
        clearInvocations(moduleClassRepository);
        when(lecturerFacade.findLecturerIdByUserId(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findPersonalTimetableForLecturer(principal, 2L, request("2026-11-16")))
                .isInstanceOfSatisfying(TimetableException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.LECTURER_NOT_FOUND));
        verifyNoInteractions(moduleClassRepository);
    }

    @Test
    void rejectsMissingPrincipalTemporaryTokenNonMondayAndWeekOutsideSemester() {
        assertThatThrownBy(() -> service.findSemesterTimetable(null, 2L, request("2026-11-16")))
                .isInstanceOfSatisfying(TimetableException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHENTICATED));
        var temporary = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("student")
                .claim("token_use", "PASSWORD_CHANGE")
                .build();
        assertThatThrownBy(() -> service.findSemesterTimetable(temporary, 2L, request("2026-11-16")))
                .isInstanceOfSatisfying(TimetableException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.FIRST_LOGIN_REQUIRED));
        assertThatThrownBy(() -> service.findSemesterTimetable(principal, 2L, request("2026-11-17")))
                .isInstanceOf(TimetableException.class);
        assertThatThrownBy(() -> service.findSemesterTimetable(principal, 2L, request("2026-11-09")))
                .isInstanceOf(TimetableException.class);
        verifyNoInteractions(moduleClassRepository);
    }

    private ModuleClass moduleClass(Long classId, Subject subject) {
        var moduleClass = ModuleClass.builder()
                .semester(semester)
                .subjectId(subject.getId())
                .moduleClassCode("MC" + classId)
                .status(ModuleClassStatus.SCHEDULED)
                .build();
        moduleClass.setId(classId);
        return moduleClass;
    }

    private ClassScheduleResponse schedule(Long scheduleId, int dayOfWeek, String startDate, String endDate) {
        return ClassScheduleResponse.builder()
                .id(scheduleId)
                .roomId(1L)
                .roomCode("101-TA1")
                .dayOfWeek(dayOfWeek)
                .lessonOrderStart(1)
                .lessonOrderEnd(3)
                .startDate(LocalDate.parse(startDate))
                .endDate(LocalDate.parse(endDate))
                .build();
    }

    private WeeklyTimetableRequest request(String weekStartDate) {
        var request = new WeeklyTimetableRequest();
        request.setWeekStartDate(LocalDate.parse(weekStartDate));
        return request;
    }

    private LearningRecordResponse enrollment(Long classId, Long semesterId) {
        return new LearningRecordResponse(
                classId,
                classId,
                "MC" + classId,
                1L,
                "CS1",
                "Học phần",
                semesterId,
                2026,
                1,
                3,
                LocalDateTime.of(2026, 11, 1, 0, 0),
                EnrollmentStatus.COMPLETED,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false);
    }
}
