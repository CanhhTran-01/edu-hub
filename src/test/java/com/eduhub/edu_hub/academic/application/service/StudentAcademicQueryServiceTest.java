package com.eduhub.edu_hub.academic.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.academic.application.exception.StudentAcademicException;
import com.eduhub.edu_hub.curriculum.api.dto.response.*;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.enrollment.api.dto.response.StudentLearningHistoryResponse;
import com.eduhub.edu_hub.enrollment.api.dto.response.StudentLearningHistoryResponse.LearningRecordResponse;
import com.eduhub.edu_hub.enrollment.application.EnrollmentFacade;
import com.eduhub.edu_hub.enrollment.domain.enums.EnrollmentStatus;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.response.StudentAcademicProfileResponse;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.domain.enums.StudentActivityStatus;
import com.eduhub.edu_hub.scheduling.api.dto.response.SemesterResponse;
import com.eduhub.edu_hub.scheduling.application.facade.SchedulingFacade;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class StudentAcademicQueryServiceTest {
    IdentityFacade identityFacade = mock(IdentityFacade.class);
    PeopleFacade.Academic peopleFacade = mock(PeopleFacade.Academic.class);
    EnrollmentFacade.StudentRecords studentRecords = mock(EnrollmentFacade.StudentRecords.class);
    CurriculumFacade.Academic curriculumFacade = mock(CurriculumFacade.Academic.class);
    SchedulingFacade.AcademicCalendar calendar = mock(SchedulingFacade.AcademicCalendar.class);
    Clock clock = Clock.fixed(Instant.parse("2026-11-16T23:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    StudentAcademicQueryService service = new StudentAcademicQueryService(
            identityFacade, peopleFacade, studentRecords, curriculumFacade, calendar, clock);
    Jwt principal = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("student")
            .claim("token_use", "ACCESS")
            .build();
    SemesterResponse previousSemester = semester(1, 2025);
    SemesterResponse currentSemester = semester(2, 2026);

    @BeforeEach
    void authenticate() {
        when(identityFacade.requireCurrentUser(principal))
                .thenReturn(UserInfoResponse.builder().id(7L).build());
        when(peopleFacade.findStudentByUserId(7L))
                .thenReturn(new StudentAcademicProfileResponse(
                        99L,
                        "SV99",
                        "Sinh viên",
                        3L,
                        "Ngành",
                        4L,
                        "K8",
                        "CT8A",
                        TrainingType.CHINH_QUY,
                        2026,
                        StudentActivityStatus.STUDYING,
                        new BigDecimal("3.25"),
                        "WARNING",
                        "Lý do đã lưu"));
        when(calendar.findCurrentSemester()).thenReturn(Optional.of(currentSemester));
        when(curriculumFacade.findCurriculum(3L, 4L, TrainingType.CHINH_QUY)).thenReturn(Optional.empty());
    }

    @Test
    void overviewKeepsFourCurrentMetricsIndependentOfTranscriptFilterAndUsesSnapshotCredits() {
        var oldFailure = enrollment(10, 1, 1, 2025, 2, EnrollmentStatus.FAILED, "0");
        var retake = enrollment(11, 1, 2, 2026, 3, EnrollmentStatus.COMPLETED, "3");
        var pending = enrollment(12, 2, 2, 2026, 4, EnrollmentStatus.REGISTERED, null);
        history(true, Set.of(1L), List.of(oldFailure, retake, pending));
        when(calendar.findSemestersByIds(Set.of(1L, 2L))).thenReturn(List.of(currentSemester, previousSemester));
        var overview = service.findMyAcademicOverview(principal);
        assertThat(overview.cumulativeGpa()).isEqualByComparingTo("3.00");
        assertThat(overview.currentSemesterGpa()).isEqualByComparingTo("3.00");
        assertThat(overview.accumulatedCredits()).isEqualTo(3);
        assertThat(overview.currentRegisteredCredits()).isEqualTo(7);
        assertThat(overview.requiredGraduationCredits()).isNull();
        assertThat(overview.currentSemesterCourseCount()).isEqualTo(2);
        assertThat(overview.currentSemesterGradedCourseCount()).isEqualTo(1);
        assertThat(overview.student().academicWarningReason()).isEqualTo("Lý do đã lưu");
        verify(studentRecords).findLearningHistory(99L);
        verify(peopleFacade).findStudentByUserId(7L);
    }

    @Test
    void groupsAllTermsNewestFirstAndKeepsPendingCoursesAndZeroScores() {
        history(
                true,
                Set.of(),
                List.of(
                        enrollment(10, 1, 1, 2025, 2, EnrollmentStatus.FAILED, "0"),
                        enrollment(11, 2, 2, 2026, 3, EnrollmentStatus.REGISTERED, null)));
        when(calendar.findSemestersByIds(Set.of(1L, 2L))).thenReturn(List.of(previousSemester, currentSemester));
        when(curriculumFacade.findPrerequisitesBySubjectIds(Set.of(1L, 2L)))
                .thenReturn(List.of(
                        new SubjectPrerequisitesResponse(1L, List.of()),
                        new SubjectPrerequisitesResponse(
                                2L,
                                List.of(SubjectSimpleResponse.builder()
                                        .id(1L)
                                        .subjectCode("CS1")
                                        .build()))));
        var transcript = service.findMyTranscript(principal, null);
        assertThat(transcript.semesters())
                .extracting(group -> group.semester().getId())
                .containsExactly(2L, 1L);
        var pendingTerm = transcript.semesters().getFirst();
        assertThat(pendingTerm.semesterGpa()).isNull();
        assertThat(pendingTerm.courses()).hasSize(1);
        assertThat(pendingTerm.courses().getFirst().enrollment().courseScore4()).isNull();
        assertThat(pendingTerm.courses().getFirst().prerequisitesMet()).isFalse();
        assertThat(transcript.semesters().getLast().semesterGpa()).isEqualByComparingTo("0");
    }

    @Test
    void filteringOldTermDoesNotUseFutureRetakeOrChangeOverviewCurrentSemester() {
        history(
                true,
                Set.of(1L),
                List.of(
                        enrollment(10, 1, 1, 2025, 2, EnrollmentStatus.FAILED, "0"),
                        enrollment(11, 1, 2, 2026, 3, EnrollmentStatus.COMPLETED, "4")));
        when(calendar.findSemestersByIds(Set.of(1L, 2L))).thenReturn(List.of(currentSemester, previousSemester));
        when(calendar.findSemester(1L)).thenReturn(previousSemester);
        var group = service.findMyTranscript(principal, 1L).semesters().getFirst();
        assertThat(group.courses()).hasSize(1);
        assertThat(group.cumulativeGpa()).isEqualByComparingTo("0");
        assertThat(group.accumulatedCredits()).isZero();
        assertThat(service.findMyAcademicOverview(principal).currentSemester().getId())
                .isEqualTo(2L);
    }

    @Test
    void emptyCurrentTermHasNullGpaAndZeroCreditsButStillAppearsInDropdown() {
        history(true, Set.of(), List.of());
        var overview = service.findMyAcademicOverview(principal);
        assertThat(overview.cumulativeGpa()).isNull();
        assertThat(overview.currentSemesterGpa()).isNull();
        assertThat(overview.currentRegisteredCredits()).isZero();
        assertThat(overview.accumulatedCredits()).isZero();
        assertThat(overview.semesters()).extracting(SemesterResponse::getId).containsExactly(2L);
        when(calendar.findCurrentSemester()).thenReturn(Optional.empty());
        assertThat(service.findMyAcademicOverview(principal).currentRegisteredCredits())
                .isNull();
    }

    @Test
    void incompleteHistoryDoesNotPretendCumulativeMetricsOrPrerequisiteFailuresAreKnown() {
        history(false, Set.of(), List.of(enrollment(10, 1, 2, 2026, 3, EnrollmentStatus.COMPLETED, "4")));
        when(calendar.findSemestersByIds(Set.of(2L))).thenReturn(List.of(currentSemester));
        when(curriculumFacade.findPrerequisitesBySubjectIds(Set.of(1L)))
                .thenReturn(List.of(new SubjectPrerequisitesResponse(
                        1L,
                        List.of(SubjectSimpleResponse.builder()
                                .id(5L)
                                .subjectCode("PRE5")
                                .build()))));
        var overview = service.findMyAcademicOverview(principal);
        assertThat(overview.cumulativeGpa()).isNull();
        assertThat(overview.accumulatedCredits()).isNull();
        assertThat(overview.historyComplete()).isFalse();
        var course = service.findMyTranscript(principal, null)
                .semesters()
                .getFirst()
                .courses()
                .getFirst();
        assertThat(course.prerequisitesMet()).isNull();
        assertThat(course.prerequisites().getFirst().passed()).isNull();
    }

    @Test
    void knownEmptySelectedTermReturnsEmptyGroupAndRegistrationSummary() {
        history(true, Set.of(), List.of());
        when(calendar.findSemester(2L)).thenReturn(currentSemester);
        var registration = service.findMySemsterRegistrations(principal, 2L);
        assertThat(registration.courses()).isEmpty();
        assertThat(registration.registeredCredits()).isZero();
        assertThat(registration.semester().getId()).isEqualTo(2L);
    }

    @Test
    void rejectsMissingPrincipalTemporaryTokenAndInvalidTermWithoutReadingOtherStudents() {
        assertThatThrownBy(() -> service.findMyAcademicOverview(null))
                .isInstanceOfSatisfying(
                        StudentAcademicException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        var temporary = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("student")
                .claim("token_use", "PASSWORD_CHANGE")
                .build();
        assertThatThrownBy(() -> service.findMyAcademicOverview(temporary))
                .isInstanceOfSatisfying(
                        StudentAcademicException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FIRST_LOGIN_REQUIRED));
        assertThatThrownBy(() -> service.findMyTranscript(principal, -1L))
                .isInstanceOfSatisfying(
                        StudentAcademicException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        verifyNoInteractions(studentRecords);
    }

    private void history(boolean complete, Set<Long> passedSubjects, List<LearningRecordResponse> courses) {
        when(studentRecords.findLearningHistory(99L))
                .thenReturn(new StudentLearningHistoryResponse(
                        complete, 2026, "Phạm vi khóa mới", passedSubjects, courses));
    }

    private SemesterResponse semester(long semesterId, int year) {
        return SemesterResponse.builder()
                .id(semesterId)
                .semesterCode("HK_1_" + year + "_" + (year + 1))
                .startDate(LocalDate.of(year, 8, 1))
                .endDate(LocalDate.of(year, 12, 31))
                .build();
    }

    private LearningRecordResponse enrollment(
            long enrollmentId,
            long subjectId,
            long semesterId,
            int year,
            int credits,
            EnrollmentStatus status,
            String grade4) {
        return new LearningRecordResponse(
                enrollmentId,
                100L + enrollmentId,
                "MC" + enrollmentId,
                subjectId,
                "CS" + subjectId,
                "Học phần " + subjectId,
                semesterId,
                year,
                1,
                credits,
                LocalDateTime.of(year, 7, 1, 0, 0),
                status,
                null,
                null,
                null,
                null,
                grade4 == null ? null : new BigDecimal("7"),
                grade4 == null ? null : "B",
                grade4 == null ? null : new BigDecimal(grade4),
                null,
                null,
                grade4 != null);
    }
}
