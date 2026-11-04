package com.eduhub.edu_hub.scheduling.infrastructure;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.api.dto.CurriculumAssignmentResponse;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.enrollment.api.dto.LearningAttemptResponse;
import com.eduhub.edu_hub.enrollment.api.dto.LearningHistoryResponse;
import com.eduhub.edu_hub.enrollment.application.EnrollmentFacade;
import com.eduhub.edu_hub.people.api.dto.StudentPlanningResponse;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.scheduling.application.port.PlanningSnapshot;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import com.eduhub.edu_hub.shared.enums.TrainingType;
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
class FacadeAcademicPlanningDataSourceTest {
    @Mock
    private PeopleFacade.Scheduling people;

    @Mock
    private CurriculumFacade.ReferenceData curriculum;

    @Mock
    private ObjectProvider<EnrollmentFacade.Planning> provider;

    @Mock
    private EnrollmentFacade.Planning enrollment;

    @InjectMocks
    private FacadeAcademicPlanningDataSource source;

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
        when(enrollment.findLearningHistory(Set.of(1L, 2L)))
                .thenReturn(new LearningHistoryResponse(
                        true, List.of(new LearningAttemptResponse(1L, "CS101", 2026, 1, "FAILED"))));

        var result = source.snapshot();

        assertThat(result.source()).isEqualTo("DATABASE");
        assertThat(result.students())
                .extracting(PlanningSnapshot.Student::curriculumCode)
                .containsExactly("CQ", "LT");
        assertThat(result.attempts()).singleElement().satisfies(attempt -> assertThat(attempt.status())
                .isEqualTo(PlanningSnapshot.Outcome.FAILED));
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
        assertUnavailable();
        verifyNoInteractions(provider, enrollment);
    }

    @Test
    void missingEnrollmentModuleIsUnavailableRatherThanAnEmptyHistory() {
        prepareStudentAndProgram();
        assertUnavailable();
    }

    @Test
    void incompleteOrInvalidHistoryCannotBeUsedForEstimatingMissedSubjects() {
        prepareStudentAndProgram();
        when(provider.getIfAvailable()).thenReturn(enrollment);
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
        return new StudentPlanningResponse(id, "SV" + id, 3L, 4L, type, year, ActivityStatus.STUDYING);
    }

    private void assertUnavailable() {
        assertThatThrownBy(source::snapshot)
                .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.PLANNING_DATA_UNAVAILABLE));
    }
}
