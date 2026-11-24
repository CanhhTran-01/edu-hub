package com.eduhub.edu_hub.people.application.facade.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.curriculum.domain.model.Major;
import com.eduhub.edu_hub.people.application.service.LecturerApplicationService;
import com.eduhub.edu_hub.people.domain.enums.LecturerStatus;
import com.eduhub.edu_hub.people.domain.enums.StudentActivityStatus;
import com.eduhub.edu_hub.people.domain.model.*;
import com.eduhub.edu_hub.people.domain.repository.LecturerRepository;
import com.eduhub.edu_hub.people.domain.repository.LecturerTeachingSubjectRepository;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PeopleSchedulingFacadeImplTest {
    @Mock
    private StudentRepository students;

    @Mock
    private LecturerApplicationService lecturers;

    @Mock
    private LecturerRepository lecturerRepository;

    @Mock
    private LecturerTeachingSubjectRepository teachingSubjectRepository;

    @InjectMocks
    private PeopleSchedulingFacadeImpl facade;

    @Test
    void resolvesLecturerIdFromUserIdWithoutLoadingAccountOrTeachingSubjects() {
        when(lecturerRepository.findLecturerIdByUserId(9L)).thenReturn(Optional.of(201L));
        assertThat(facade.findLecturerIdByUserId(9L)).contains(201L);
        verifyNoInteractions(students, lecturers, teachingSubjectRepository);
    }

    @Test
    void loadsTeachingCandidatesWithoutStudentOrAccountQueries() {
        var lecturer = Lecturer.builder()
                .fullName("Giảng viên")
                .status(LecturerStatus.ACTIVE)
                .build();
        lecturer.setId(201L);
        when(lecturerRepository.findTimetableCandidates(Set.of(1L), LecturerStatus.ACTIVE))
                .thenReturn(List.of(lecturer));
        when(teachingSubjectRepository.findByLecturerIdInAndDeletedFalseOrderByLecturerIdAscSubjectIdAsc(List.of(201L)))
                .thenReturn(List.of(LecturerTeachingSubject.builder()
                        .lecturerId(201L)
                        .subjectId(1L)
                        .build()));
        assertThat(facade.findEligibleLecturers(Set.of(1L))).singleElement().satisfies(candidate -> {
            assertThat(candidate.lecturerId()).isEqualTo(201L);
            assertThat(candidate.teachingSubjectIds()).containsExactly(1L);
        });
        verifyNoInteractions(students, lecturers);
    }

    @Test
    void locksLecturerProfilesInIdOrderBeforeReadingTeachingLinks() {
        var first = Lecturer.builder().status(LecturerStatus.ACTIVE).build();
        first.setId(201L);
        var second = Lecturer.builder().status(LecturerStatus.ACTIVE).build();
        second.setId(202L);
        when(lecturerRepository.findLecturerProfileByIdForUpdate(201L)).thenReturn(Optional.of(first));
        when(lecturerRepository.findLecturerProfileByIdForUpdate(202L)).thenReturn(Optional.of(second));
        facade.lockLecturersForTimetabling(Set.of(202L, 201L));
        var ordering = inOrder(lecturerRepository, teachingSubjectRepository);
        ordering.verify(lecturerRepository).findLecturerProfileByIdForUpdate(201L);
        ordering.verify(lecturerRepository).findLecturerProfileByIdForUpdate(202L);
        ordering.verify(teachingSubjectRepository)
                .findByLecturerIdInAndDeletedFalseOrderByLecturerIdAscSubjectIdAsc(List.of(201L, 202L));
    }

    @Test
    void studyingStudentWithoutAnAccountIsIncludedAndUsesTheCohortStartYear() {
        var cohort = new Cohort();
        cohort.setId(2L);
        cohort.setStartYear(2026);
        cohort.setEndYear(2030);
        var administrativeClass = new AdministrativeClass();
        var major = Major.builder().build();
        major.setId(3L);
        administrativeClass.setMajorId(major.getId());
        administrativeClass.setCohort(cohort);
        administrativeClass.setTrainingType(TrainingType.CHINH_QUY);
        var student = new Student();
        student.setId(4L);
        student.setStudentCode("SV04");
        student.setActivityStatus(StudentActivityStatus.STUDYING);
        student.setAdministrativeClass(administrativeClass);
        when(students.findAllByDeletedFalseAndActivityStatus(StudentActivityStatus.STUDYING))
                .thenReturn(List.of(student));

        var result = facade.findStudentsForPlanning();

        assertThat(student.getUserId()).isNull();
        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(4L);
            assertThat(item.admissionYear()).isEqualTo(2026);
            assertThat(item.majorId()).isEqualTo(3L);
        });
        // Thiếu thông tin khóa phải được giữ lại để bên lập kế hoạch báo thiếu dữ liệu.
        cohort.setDeleted(true);
        assertThat(facade.findStudentsForPlanning()).singleElement().satisfies(item -> {
            assertThat(item.cohortId()).isNull();
            assertThat(item.admissionYear()).isNull();
        });
        verifyNoInteractions(lecturers);
    }
}
