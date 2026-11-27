package com.eduhub.edu_hub.academic.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.academic.api.dto.request.PrerequisiteImpactRequest;
import com.eduhub.edu_hub.academic.application.exception.StudentAcademicException;
import com.eduhub.edu_hub.academic.domain.enums.*;
import com.eduhub.edu_hub.curriculum.api.dto.response.*;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.domain.enums.*;
import com.eduhub.edu_hub.enrollment.api.dto.response.*;
import com.eduhub.edu_hub.enrollment.api.dto.response.StudentLearningHistoryResponse.LearningRecordResponse;
import com.eduhub.edu_hub.enrollment.application.facade.EnrollmentFacade;
import com.eduhub.edu_hub.enrollment.domain.enums.EnrollmentStatus;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.response.StudentAcademicProfileResponse;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.domain.enums.StudentActivityStatus;
import com.eduhub.edu_hub.scheduling.api.dto.response.SemesterResponse;
import com.eduhub.edu_hub.scheduling.application.facade.SchedulingFacade;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class StudentPrerequisiteImpactServiceTest {
    IdentityFacade identity = mock(IdentityFacade.class);
    PeopleFacade.Academic people = mock(PeopleFacade.Academic.class);
    EnrollmentFacade.StudentRecords records = mock(EnrollmentFacade.StudentRecords.class);
    CurriculumFacade.Academic curriculum = mock(CurriculumFacade.Academic.class);
    SchedulingFacade.AcademicCalendar calendar = mock(SchedulingFacade.AcademicCalendar.class);
    Clock clock = Clock.fixed(Instant.parse("2026-11-27T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    StudentPrerequisiteImpactService service =
            new StudentPrerequisiteImpactService(identity, people, records, curriculum, calendar, clock);
    Jwt principal = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .claim("token_use", "ACCESS")
            .subject("student")
            .build();
    SemesterResponse current = semester(2L, 2026);
    Map<Long, List<Long>> graph = new HashMap<>(Map.of(1L, List.of(), 2L, List.of(1L), 3L, List.of(2L)));

    @BeforeEach
    void prepare() {
        when(identity.requireCurrentUser(principal))
                .thenReturn(UserInfoResponse.builder().id(7L).role("STUDENT").build());
        when(people.findStudentByUserId(7L))
                .thenReturn(new StudentAcademicProfileResponse(
                        99L,
                        "PRIVATE_CODE",
                        "PRIVATE_NAME",
                        3L,
                        "Ngành",
                        4L,
                        "K8",
                        "Lớp",
                        TrainingType.CHINH_QUY,
                        2026,
                        StudentActivityStatus.STUDYING,
                        null,
                        null,
                        null,
                        null));
        when(calendar.findCurrentSemester()).thenReturn(Optional.of(current));
        when(calendar.findSemestersByIds(anySet()))
                .thenReturn(List.of(semester(1L, 2025), current, semester(3L, 2027)));
        when(records.findPassedSubjectSemesters(99L)).thenReturn(List.of());
        when(records.findLearningHistory(99L)).thenReturn(history(true, List.of(course(1L, 1L), course(2L, 2L))));
        when(curriculum.findCurriculum(3L, 4L, TrainingType.CHINH_QUY)).thenReturn(Optional.of(curriculum()));
        when(curriculum.findPrerequisitesBySubjectIds(anySet())).thenAnswer(invocation -> {
            Set<Long> ids = invocation.getArgument(0);
            return ids.stream()
                    .sorted()
                    .map(subjectId -> new SubjectPrerequisitesResponse(
                            subjectId,
                            graph.getOrDefault(subjectId, List.of()).stream()
                                    .map(prerequisiteId -> SubjectSimpleResponse.builder()
                                            .id(prerequisiteId)
                                            .subjectCode("S" + prerequisiteId)
                                            .subjectName("Môn " + prerequisiteId)
                                            .credits(3)
                                            .build())
                                    .toList()))
                    .toList();
        });
    }

    @Test
    void semesterOverviewOnlyListsRegisteredCoursesAndTreatsTheirFailuresIndependently() {
        var response = service.analyzeMyPrerequisites(principal, overview());
        assertThat(response.status()).isEqualTo(PrerequisiteAnalysisStatus.COMPLETED);
        assertThat(response.courses())
                .extracting(course -> course.subject().subjectId())
                .containsExactly(1L, 2L);
        assertThat(response.courses().getFirst().directImpacts())
                .extracting(impact -> impact.subject().subjectId())
                .containsExactly(2L);
        assertThat(response.courses().getFirst().indirectImpacts())
                .extracting(impact -> impact.subject().subjectId())
                .containsExactly(3L);
        assertThat(response.courses().get(1).directImpacts())
                .extracting(impact -> impact.subject().subjectId())
                .containsExactly(3L);
        verify(records).findLearningHistory(99L);
        verify(records).findPassedSubjectSemesters(99L);
    }

    @Test
    void currentSemesterPassingResultDoesNotSatisfyTheSameSemesterEntryButOpensFutureRequirements() {
        when(records.findPassedSubjectSemesters(99L)).thenReturn(List.of(new PassedSubjectSemesterResponse(1L, 2L)));
        var response = service.analyzeMyPrerequisites(principal, overview());
        assertThat(response.courses().getFirst().failureAssumptionApplicable()).isFalse();
        assertThat(response.courses().get(1).prerequisites().getFirst().passedBeforeSemester())
                .isFalse();
    }

    @Test
    void priorPassingResultsIncludingHiddenAttemptsStopTheDependentChain() {
        when(records.findPassedSubjectSemesters(99L)).thenReturn(List.of(new PassedSubjectSemesterResponse(2L, 1L)));
        var response = service.analyzeMyPrerequisites(principal, impact("S1"));
        assertThat(response.courses().getFirst().directImpacts()).isEmpty();
        assertThat(response.courses().getFirst().indirectImpacts()).isEmpty();
    }

    @Test
    void futurePassingResultsAreExcludedFromBothEntryAndFailureAnalysis() {
        when(records.findPassedSubjectSemesters(99L)).thenReturn(List.of(new PassedSubjectSemesterResponse(1L, 3L)));
        var response = service.analyzeMyPrerequisites(principal, overview());
        assertThat(response.courses().getFirst().failureAssumptionApplicable()).isTrue();
        assertThat(response.courses().get(1).prerequisites().getFirst().passedBeforeSemester())
                .isFalse();
    }

    @Test
    void individualCurriculumCourseIsLabeledAsNotRegisteredInTheCurrentSemester() {
        var response = service.analyzeMyPrerequisites(principal, impact("S3"));
        assertThat(response.courses()).hasSize(1);
        assertThat(response.courses().getFirst().registeredInCurrentSemester()).isFalse();
        assertThat(response.courses().getFirst().enrollmentId()).isNull();
    }

    @Test
    void ambiguousOrMissingReferencesAskInsteadOfSelectingACourse() {
        var response = service.analyzeMyPrerequisites(principal, impact("Môn"));
        assertThat(response.status()).isEqualTo(PrerequisiteAnalysisStatus.NEEDS_CLARIFICATION);
        assertThat(response.message()).contains("S1", "S2", "S3");
        assertThat(response.pendingCourseReferences()).containsExactly("Môn");
        assertThat(service.analyzeMyPrerequisites(principal, impact("Không tồn tại"))
                        .status())
                .isEqualTo(PrerequisiteAnalysisStatus.NEEDS_CLARIFICATION);
    }

    @Test
    void missingCurriculumOrHistoryDoesNotBecomeAnAllSatisfiedEmptyGraph() {
        when(curriculum.findCurriculum(3L, 4L, TrainingType.CHINH_QUY)).thenReturn(Optional.empty());
        when(records.findLearningHistory(99L)).thenReturn(history(false, List.of(course(1L, 1L), course(2L, 2L))));
        var response = service.analyzeMyPrerequisites(principal, overview());
        assertThat(response.status()).isEqualTo(PrerequisiteAnalysisStatus.DATA_INCOMPLETE);
        assertThat(response.graphComplete()).isFalse();
        assertThat(response.prerequisiteStatus()).isEqualTo(AcademicRequirementStatus.INSUFFICIENT_DATA);
        assertThat(response.courses().get(1).prerequisites().getFirst().passedBeforeSemester())
                .isNull();
    }

    @Test
    void externalHistoricalPrerequisiteIsLoadedThroughTheFacadeClosure() {
        graph.put(1L, List.of(99L));
        var response = service.analyzeMyPrerequisites(principal, overview());
        assertThat(response.courses()
                        .getFirst()
                        .prerequisites()
                        .getFirst()
                        .subject()
                        .subjectId())
                .isEqualTo(99L);
        verify(curriculum).findPrerequisitesBySubjectIds(Set.of(99L));
    }

    @Test
    void cyclesAndMissingSemesterMetadataProduceIncompleteResults() {
        graph.put(1L, List.of(3L));
        var response = service.analyzeMyPrerequisites(principal, overview());
        assertThat(response.status()).isEqualTo(PrerequisiteAnalysisStatus.DATA_INCOMPLETE);
        assertThat(response.graphComplete()).isFalse();
        when(calendar.findCurrentSemester()).thenReturn(Optional.empty());
        assertThat(service.analyzeMyPrerequisites(principal, overview()).semester())
                .isNull();
    }

    @Test
    void nullPrincipalAndNonStudentRolesAreRejectedBeforeReadingPrivateHistory() {
        assertThatThrownBy(() -> service.analyzeMyPrerequisites(null, overview()))
                .isInstanceOfSatisfying(StudentAcademicException.class, failure -> assertThat(failure.getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHENTICATED));
        when(identity.requireCurrentUser(principal))
                .thenReturn(UserInfoResponse.builder().id(7L).role("LECTURER").build());
        assertThatThrownBy(() -> service.analyzeMyPrerequisites(principal, overview()))
                .isInstanceOfSatisfying(StudentAcademicException.class, failure -> assertThat(failure.getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED));
        verifyNoInteractions(records);
    }

    private PrerequisiteImpactRequest overview() {
        return new PrerequisiteImpactRequest(PrerequisiteAnalysisScope.SEMESTER_OVERVIEW, List.of());
    }

    private PrerequisiteImpactRequest impact(String reference) {
        return new PrerequisiteImpactRequest(PrerequisiteAnalysisScope.COURSE_IMPACT, List.of(reference));
    }

    private StudentLearningHistoryResponse history(boolean complete, List<LearningRecordResponse> courses) {
        return new StudentLearningHistoryResponse(complete, 2026, "Phạm vi khóa mới", Set.of(), courses);
    }

    private SemesterResponse semester(Long id, int year) {
        return SemesterResponse.builder()
                .id(id)
                .semesterName("Kỳ " + year)
                .startDate(LocalDate.of(year, 8, 1))
                .build();
    }

    private StudentCurriculumResponse curriculum() {
        return new StudentCurriculumResponse(
                1L,
                "CTDT",
                "Chương trình",
                3L,
                "Ngành",
                4L,
                "K8",
                "CHINH_QUY",
                8,
                120,
                120,
                List.of(new StudentCurriculumResponse.PlannedSemesterResponse(
                        1,
                        List.of(
                                new StudentCurriculumResponse.PlannedSubjectResponse(
                                        1L, "S1", "Môn 1", 3, SubjectType.REQUIRED),
                                new StudentCurriculumResponse.PlannedSubjectResponse(
                                        2L, "S2", "Môn 2", 3, SubjectType.REQUIRED),
                                new StudentCurriculumResponse.PlannedSubjectResponse(
                                        3L, "S3", "Môn 3", 3, SubjectType.REQUIRED)))));
    }

    private LearningRecordResponse course(Long enrollmentId, Long subjectId) {
        return new LearningRecordResponse(
                enrollmentId,
                enrollmentId + 100,
                "MC" + enrollmentId,
                subjectId,
                "S" + subjectId,
                "Môn " + subjectId,
                2L,
                2026,
                1,
                3,
                LocalDateTime.now(clock),
                EnrollmentStatus.REGISTERED,
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
