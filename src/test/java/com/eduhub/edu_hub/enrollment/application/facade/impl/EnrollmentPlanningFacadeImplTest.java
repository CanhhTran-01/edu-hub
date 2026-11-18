package com.eduhub.edu_hub.enrollment.application.facade.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.enrollment.domain.enums.EnrollmentStatus;
import com.eduhub.edu_hub.enrollment.domain.model.Enrollment;
import com.eduhub.edu_hub.enrollment.domain.repository.EnrollmentRepository;
import com.eduhub.edu_hub.enrollment.infrastructure.config.EnrollmentRegistrationSettings;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import java.util.*;
import org.junit.jupiter.api.*;

class EnrollmentPlanningFacadeImplTest {
    private final EnrollmentRepository repository = mock(EnrollmentRepository.class);
    private final PeopleFacade.Enrollment people = mock(PeopleFacade.Enrollment.class);
    private final EnrollmentRegistrationSettings settings = new EnrollmentRegistrationSettings();
    private EnrollmentPlanningFacadeImpl facade;

    @BeforeEach
    void setUp() {
        settings.setFirstSupportedAdmissionYear(2026);
        facade = new EnrollmentPlanningFacadeImpl(repository, people, settings);
    }

    @Test
    void emptyHistoryIsCompleteOnlyForSupportedStudentsWithKnownAdmissionYear() {
        when(people.findAdmissionYearsByStudentIds(Set.of(1L))).thenReturn(Map.of(1L, 2026));
        assertThat(facade.findLearningHistory(Set.of(1L)).historyComplete()).isTrue();
        when(people.findAdmissionYearsByStudentIds(Set.of(1L))).thenReturn(Map.of(1L, 2025));
        assertThat(facade.findLearningHistory(Set.of(1L)).historyComplete()).isFalse();
        when(people.findAdmissionYearsByStudentIds(Set.of(1L))).thenReturn(Map.of());
        assertThat(facade.findLearningHistory(Set.of(1L)).historyComplete()).isFalse();
    }

    @Test
    void classUsagePreservesDroppedAndHiddenHistoryButActiveUsageCountsSeparately() {
        var dropped = Enrollment.builder()
                .studentId(1L)
                .status(EnrollmentStatus.DROPPED)
                .build();
        dropped.setDeleted(true);
        var registered = Enrollment.builder()
                .studentId(1L)
                .status(EnrollmentStatus.REGISTERED)
                .build();
        when(repository.findAllByModuleClassIdOrderByIdAsc(100L)).thenReturn(List.of(dropped, registered));
        when(repository.countByModuleClassIdAndStatusAndDeletedFalse(100L, EnrollmentStatus.REGISTERED))
                .thenReturn(1L);
        var usage = facade.summarizeModuleClass(100L);
        assertThat(usage.enrollmentCount()).isEqualTo(2);
        assertThat(usage.studentCount()).isEqualTo(1);
        assertThat(facade.countActiveRegistrations(100L)).isEqualTo(1);
    }

    @Test
    void rejectsMissingOrInvertedRegistrationSettings() {
        assertThatThrownBy(settings::validate)
                .isInstanceOf(com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException.class);
        settings.setMinimumCredits(21);
        settings.setMaximumCredits(20);
        assertThatThrownBy(settings::validate)
                .isInstanceOf(com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException.class);
        settings.setMinimumCredits(12);
        settings.validate();
    }
}
