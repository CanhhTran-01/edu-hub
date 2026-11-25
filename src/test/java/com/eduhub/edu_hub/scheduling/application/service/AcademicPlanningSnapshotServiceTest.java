package com.eduhub.edu_hub.scheduling.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.api.dto.response.CurriculumAssignmentResponse;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.enrollment.api.dto.response.LearningAttemptResponse;
import com.eduhub.edu_hub.enrollment.api.dto.response.LearningHistoryResponse;
import com.eduhub.edu_hub.enrollment.application.facade.EnrollmentFacade;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.people.application.facade.dto.StudentPlanningResponse;
import com.eduhub.edu_hub.people.domain.enums.StudentActivityStatus;
import com.eduhub.edu_hub.scheduling.domain.enums.LearningOutcome;
import com.eduhub.edu_hub.scheduling.domain.model.AcademicPlanningSnapshot;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class AcademicPlanningSnapshotServiceTest {
    @Mock
    private PeopleFacade.Scheduling people;

    @Mock
    private CurriculumFacade.ReferenceData curriculum;

    @Mock
    private ObjectProvider<EnrollmentFacade.Planning> provider;

    @Mock
    private EnrollmentFacade.Planning enrollment;

    @InjectMocks
    private AcademicPlanningSnapshotService academicPlanningSnapshotService;

    @Test
    void matchesTheProgramByMajorCohortAndTrainingTypeAndMapsLearningHistory() {
        when(people.findStudentsForPlanning())
                .thenReturn(
                        List.of(student(1L, TrainingType.CHINH_QUY, 2026), student(2L, TrainingType.LIEN_THONG, 2026)));
        when(curriculum.findCurriculumAssignments(Set.of(3L), Set.of(4L)))
                .thenReturn(List.of(
                        new CurriculumAssignmentResponse(10L, "CQ", 3L, 4L, TrainingType.CHINH_QUY),
                        new CurriculumAssignmentResponse(11L, "LT", 3L, 4L, TrainingType.LIEN_THONG)));
        when(provider.getIfAvailable()).thenReturn(enrollment);
        when(enrollment.findScope())
                .thenReturn(new com.eduhub.edu_hub.enrollment.api.dto.response.EnrollmentScopeResponse(
                        2026, "Phạm vi khóa mới"));
        when(enrollment.findLearningHistory(Set.of(1L, 2L)))
                .thenReturn(new LearningHistoryResponse(
                        true, List.of(new LearningAttemptResponse(1L, "CS101", 2026, 1, "FAILED"))));

        var result = academicPlanningSnapshotService.loadPlanningSnapshot();

        assertThat(result.source()).isEqualTo("DATABASE");
        assertThat(result.students())
                .extracting(AcademicPlanningSnapshot.PlanningStudent::curriculumCode)
                .containsExactly("CQ", "LT");
        assertThat(result.attempts()).singleElement().satisfies(attempt -> assertThat(attempt.status())
                .isEqualTo(LearningOutcome.FAILED));
    }

    @Test
    void missingCohortYearIsNotGuessedFromTheStudentCode() {
        when(people.findStudentsForPlanning()).thenReturn(List.of(student(1L, TrainingType.CHINH_QUY, null)));
        assertUnavailable();
        verifyNoInteractions(curriculum, provider, enrollment);
    }

    @Test
    void missingCurriculumAssignmentDoesNotSilentlyExcludeTheStudent() {
        when(people.findStudentsForPlanning()).thenReturn(List.of(student(1L, TrainingType.CHINH_QUY, 2026)));
        when(provider.getIfAvailable()).thenReturn(enrollment);
        when(enrollment.findScope())
                .thenReturn(new com.eduhub.edu_hub.enrollment.api.dto.response.EnrollmentScopeResponse(
                        2026, "Phạm vi khóa mới"));
        assertUnavailable();
        verify(enrollment, never()).findLearningHistory(anySet());
    }

    @Test
    void missingEnrollmentModuleIsUnavailableRatherThanAnEmptyHistory() {
        when(people.findStudentsForPlanning()).thenReturn(List.of(student(1L, TrainingType.CHINH_QUY, 2026)));
        assertUnavailable();
    }

    @Test
    void excludesUnmigratedCohortsAndReturnsTheScopeAlongsideCounts() {
        when(people.findStudentsForPlanning()).thenReturn(List.of(student(1L, TrainingType.CHINH_QUY, 2025)));
        when(provider.getIfAvailable()).thenReturn(enrollment);
        when(enrollment.findScope())
                .thenReturn(new com.eduhub.edu_hub.enrollment.api.dto.response.EnrollmentScopeResponse(
                        2026, "Chưa bao gồm khóa cũ"));
        var snapshot = academicPlanningSnapshotService.loadPlanningSnapshot();
        assertThat(snapshot.students()).isEmpty();
        assertThat(snapshot.excludedStudentCount()).isEqualTo(1);
        assertThat(snapshot.scopeNotice()).contains("khóa cũ");
        verifyNoInteractions(curriculum);
        verify(enrollment, never()).findLearningHistory(anySet());
    }

    @Test
    void incompleteOrInvalidHistoryCannotBeUsedForEstimatingMissedSubjects() {
        prepareStudentAndProgram();
        when(provider.getIfAvailable()).thenReturn(enrollment);
        when(enrollment.findScope())
                .thenReturn(new com.eduhub.edu_hub.enrollment.api.dto.response.EnrollmentScopeResponse(
                        2026, "Phạm vi khóa mới"));
        when(enrollment.findLearningHistory(Set.of(1L))).thenReturn(new LearningHistoryResponse(false, List.of()));
        assertUnavailable();
        when(enrollment.findLearningHistory(Set.of(1L)))
                .thenReturn(new LearningHistoryResponse(
                        true, List.of(new LearningAttemptResponse(1L, "CS101", 2026, 1, "UNKNOWN"))));
        assertUnavailable();
    }

    private void prepareStudentAndProgram() {
        when(people.findStudentsForPlanning()).thenReturn(List.of(student(1L, TrainingType.CHINH_QUY, 2026)));
        when(curriculum.findCurriculumAssignments(Set.of(3L), Set.of(4L)))
                .thenReturn(List.of(new CurriculumAssignmentResponse(10L, "CQ", 3L, 4L, TrainingType.CHINH_QUY)));
    }

    private StudentPlanningResponse student(Long id, TrainingType type, Integer year) {
        return new StudentPlanningResponse(id, "SV" + id, 3L, 4L, type, year, StudentActivityStatus.STUDYING);
    }

    private void assertUnavailable() {
        assertThatThrownBy(academicPlanningSnapshotService::loadPlanningSnapshot)
                .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.PLANNING_DATA_UNAVAILABLE));
    }
}
