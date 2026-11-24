package com.eduhub.edu_hub.people.application.facade.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.api.dto.response.MajorSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.people.api.dto.response.StudentRosterProfileResponse;
import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import com.eduhub.edu_hub.people.domain.model.Cohort;
import com.eduhub.edu_hub.people.domain.model.Student;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PeopleEnrollmentFacadeImplTest {
    private final StudentRepository students = mock(StudentRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final CurriculumFacade.ReferenceData curriculumReferenceData = mock(CurriculumFacade.ReferenceData.class);
    private final PeopleEnrollmentFacadeImpl facade =
            new PeopleEnrollmentFacadeImpl(students, entityManager, curriculumReferenceData);

    @Test
    void rosterProfilesUseBatchesWithoutLoadingManagedStudentsOrAccounts() {
        var studentIds = LongStream.rangeClosed(1, 501).boxed().collect(Collectors.toSet());
        var firstBatch = LongStream.rangeClosed(1, 500).boxed().toList();
        when(students.findRosterProfilesByIds(firstBatch))
                .thenReturn(List.of(new StudentRosterProfileResponse(1L, "00001", "Sinh viên cũ")));
        when(students.findRosterProfilesByIds(List.of(501L)))
                .thenReturn(List.of(new StudentRosterProfileResponse(501L, "00501", "Sinh viên mới")));
        assertThat(facade.findRosterStudentsByIds(studentIds))
                .extracting(StudentRosterProfileResponse::studentId)
                .containsExactly(1L, 501L);
        verifyNoInteractions(entityManager);
        verify(students).findRosterProfilesByIds(firstBatch);
        verify(students).findRosterProfilesByIds(List.of(501L));
        verifyNoMoreInteractions(students);
    }

    @Test
    void emptyRosterDoesNotQueryPeople() {
        assertThat(facade.findRosterStudentsByIds(Set.of())).isEmpty();
        verifyNoInteractions(students, entityManager);
    }

    @Test
    void gradeSearchNormalizesAndEscapesKeywordsBeforeThePeopleQuery() {
        when(students.findRosterStudentIdsByKeyword("%sv!%!_!!%")).thenReturn(Set.of(7L));
        assertThat(facade.findRosterStudentIdsByKeyword("  SV%_!  ")).containsExactly(7L);
        verify(students).findRosterStudentIdsByKeyword("%sv!%!_!!%");
        assertThat(facade.findRosterStudentIdsByKeyword("  ")).isEmpty();
        verifyNoMoreInteractions(students);
        verifyNoInteractions(entityManager);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void studentLookupPreservesTheDeletedMajorRuleWithoutLosingCohortHistory(boolean majorDeleted) {
        var cohort = new Cohort();
        cohort.setId(8L);
        cohort.setStartYear(2026);
        var administrativeClass = AdministrativeClass.builder()
                .majorId(2L)
                .cohort(cohort)
                .trainingType(TrainingType.CHINH_QUY)
                .build();
        var student = Student.builder()
                .administrativeClass(administrativeClass)
                .activityStatus(ActivityStatus.DEACTIVATED)
                .build();
        student.setId(9L);
        when(students.findByUserIdAndDeletedFalse(7L)).thenReturn(Optional.of(student));
        when(curriculumReferenceData.findMajorsByIds(Set.of(2L)))
                .thenReturn(List.of(new MajorSimpleResponse(2L, "CNTT", "Ngành cũ", null, majorDeleted)));

        var profile = facade.findStudentByUserId(7L);

        assertThat(profile.majorId()).isEqualTo(majorDeleted ? null : 2L);
        assertThat(profile.cohortId()).isEqualTo(8L);
        assertThat(profile.admissionYear()).isEqualTo(2026);
        assertThat(profile.activityStatus()).isEqualTo(ActivityStatus.DEACTIVATED);
        verifyNoInteractions(entityManager);
    }

    @Test
    void admissionYearsDoNotLoadMajorDataForEachStudent() {
        var cohort = new Cohort();
        cohort.setStartYear(2026);
        var student = Student.builder()
                .administrativeClass(
                        AdministrativeClass.builder().majorId(2L).cohort(cohort).build())
                .build();
        student.setId(9L);
        when(students.findAllById(Set.of(9L))).thenReturn(List.of(student));

        assertThat(facade.findAdmissionYearsByStudentIds(Set.of(9L))).containsEntry(9L, 2026);
        verifyNoInteractions(curriculumReferenceData, entityManager);
    }
}
