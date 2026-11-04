package com.eduhub.edu_hub.people.application.facade.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.domain.model.Major;
import com.eduhub.edu_hub.people.application.LecturerApplicationService;
import com.eduhub.edu_hub.people.domain.model.*;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import com.eduhub.edu_hub.shared.enums.TrainingType;
import java.util.List;
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

    @InjectMocks
    private PeopleSchedulingFacadeImpl facade;

    @Test
    void studyingStudentWithoutAnAccountIsIncludedAndUsesTheCohortStartYear() {
        var cohort = new Cohort();
        cohort.setId(2L);
        cohort.setStartYear(2026);
        cohort.setEndYear(2030);
        var administrativeClass = new AdministrativeClass();
        var major = Major.builder().build();
        major.setId(3L);
        administrativeClass.setMajor(major);
        administrativeClass.setCohort(cohort);
        administrativeClass.setTrainingType(TrainingType.CHINH_QUY);
        var student = new Student();
        student.setId(4L);
        student.setStudentCode("SV04");
        student.setActivityStatus(ActivityStatus.STUDYING);
        student.setAdministrativeClass(administrativeClass);
        when(students.findAllByDeletedFalseAndActivityStatus(ActivityStatus.STUDYING))
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
