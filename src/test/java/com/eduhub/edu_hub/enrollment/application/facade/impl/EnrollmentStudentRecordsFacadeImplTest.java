package com.eduhub.edu_hub.enrollment.application.facade.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.enrollment.domain.enums.EnrollmentStatus;
import com.eduhub.edu_hub.enrollment.domain.model.Enrollment;
import com.eduhub.edu_hub.enrollment.domain.repository.EnrollmentRepository;
import com.eduhub.edu_hub.enrollment.infrastructure.config.EnrollmentRegistrationSettings;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import java.util.*;
import org.junit.jupiter.api.Test;

class EnrollmentStudentRecordsFacadeImplTest {
    EnrollmentRepository enrollmentRepository = mock(EnrollmentRepository.class);
    PeopleFacade.Enrollment peopleFacade = mock(PeopleFacade.Enrollment.class);
    EnrollmentRegistrationSettings settings = new EnrollmentRegistrationSettings();
    EnrollmentStudentRecordsFacadeImpl facade = new EnrollmentStudentRecordsFacadeImpl(
            enrollmentRepository,
            peopleFacade,
            settings,
            java.time.Clock.fixed(
                    java.time.Instant.parse("2026-11-23T03:00:00Z"), java.time.ZoneId.of("Asia/Ho_Chi_Minh")));

    @Test
    void excludesDroppedAndHiddenRowsButPreservesHistoricalPassForPrerequisites() {
        settings.setFirstSupportedAdmissionYear(2026);
        var registered = enrollment(1, EnrollmentStatus.REGISTERED);
        var passed = enrollment(2, EnrollmentStatus.COMPLETED);
        passed.setDeleted(true);
        var dropped = enrollment(3, EnrollmentStatus.DROPPED);
        var failed = enrollment(4, EnrollmentStatus.FAILED);
        when(enrollmentRepository.findAllByStudentIdOrderByRegisteredAtAscIdAsc(77L))
                .thenReturn(List.of(registered, passed, dropped, failed));
        when(peopleFacade.findAdmissionYearsByStudentIds(Set.of(77L))).thenReturn(Map.of(77L, 2026));
        var history = facade.findLearningHistory(77L);
        assertThat(history.historyComplete()).isTrue();
        assertThat(history.enrollments())
                .extracting(enrollment -> enrollment.enrollmentId())
                .containsExactly(1L, 4L);
        assertThat(history.passedSubjectIds()).containsExactly(2L);
        assertThat(history.enrollments().getFirst().credits()).isEqualTo(3);
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void doesNotTreatEmptyLegacyHistoryAsComplete() {
        settings.setFirstSupportedAdmissionYear(2026);
        when(enrollmentRepository.findAllByStudentIdOrderByRegisteredAtAscIdAsc(77L))
                .thenReturn(List.of());
        when(peopleFacade.findAdmissionYearsByStudentIds(Set.of(77L))).thenReturn(Map.of(77L, 2025));
        assertThat(facade.findLearningHistory(77L).historyComplete()).isFalse();
        when(peopleFacade.findAdmissionYearsByStudentIds(Set.of(77L))).thenReturn(Map.of());
        assertThat(facade.findLearningHistory(77L).historyComplete()).isFalse();
    }

    private Enrollment enrollment(long enrollmentId, EnrollmentStatus status) {
        var enrollment = Enrollment.builder()
                .studentId(77L)
                .subjectId(enrollmentId)
                .credits(3)
                .status(status)
                .build();
        enrollment.setId(enrollmentId);
        return enrollment;
    }

    @Test
    void exposesPublishedGradesAndTheExactLockDeadlineToStudentAcademicQueries() {
        settings.setFirstSupportedAdmissionYear(2026);
        var graded = enrollment(1, EnrollmentStatus.REGISTERED);
        var publishedAt = java.time.LocalDateTime.parse("2026-11-09T10:00:00");
        graded.updateCourseGrade(
                new java.math.BigDecimal("9"),
                new java.math.BigDecimal("8"),
                "B",
                new java.math.BigDecimal("3"),
                EnrollmentStatus.COMPLETED,
                publishedAt,
                publishedAt.plusDays(14),
                publishedAt);
        when(enrollmentRepository.findAllByStudentIdOrderByRegisteredAtAscIdAsc(77L))
                .thenReturn(List.of(graded));
        when(peopleFacade.findAdmissionYearsByStudentIds(Set.of(77L))).thenReturn(Map.of(77L, 2026));
        var displayed = facade.findLearningHistory(77L).enrollments().getFirst();
        assertThat(displayed.finalExamScore()).isEqualByComparingTo("9");
        assertThat(displayed.gradePublishedAt()).isEqualTo(publishedAt);
        assertThat(displayed.gradeLockAt()).isEqualTo(publishedAt.plusDays(14));
        assertThat(displayed.gradeLocked()).isTrue();
    }
}
