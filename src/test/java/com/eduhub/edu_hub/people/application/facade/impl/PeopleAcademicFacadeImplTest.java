package com.eduhub.edu_hub.people.application.facade.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.application.facade.dto.MajorSimpleResponse;
import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.curriculum.domain.model.Major;
import com.eduhub.edu_hub.people.domain.model.*;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class PeopleAcademicFacadeImplTest {
    StudentRepository studentRepository = mock(StudentRepository.class);
    CurriculumFacade.ReferenceData referenceData = mock(CurriculumFacade.ReferenceData.class);
    PeopleAcademicFacadeImpl facade = new PeopleAcademicFacadeImpl(studentRepository, referenceData);

    @Test
    void readsInactiveStudentProfileByUserIdWithoutChangingWarningOrTarget() {
        var major = Major.builder().build();
        major.setId(2L);
        var cohort = new Cohort();
        cohort.setCohortCode("K8");
        cohort.setStartYear(2026);
        cohort.setId(3L);
        var administrativeClass = AdministrativeClass.builder()
                .className("CT8A")
                .major(major)
                .cohort(cohort)
                .trainingType(TrainingType.CHINH_QUY)
                .build();
        var student = Student.builder()
                .studentCode("SV99")
                .fullName("Sinh viên")
                .administrativeClass(administrativeClass)
                .activityStatus(ActivityStatus.DEACTIVATED)
                .targetGpa(new BigDecimal("3.25"))
                .academicWarningStatus("WARNING")
                .academicWarningReason("Lý do đã lưu")
                .build();
        student.setId(99L);
        when(studentRepository.findByUserIdAndDeletedFalse(7L)).thenReturn(Optional.of(student));
        when(referenceData.findMajorsByIds(Set.of(2L)))
                .thenReturn(List.of(new MajorSimpleResponse(2L, "CNTT", "Công nghệ thông tin", null)));
        var profile = facade.findStudentByUserId(7L);
        assertThat(profile.studentId()).isEqualTo(99L);
        assertThat(profile.activityStatus()).isEqualTo(ActivityStatus.DEACTIVATED);
        assertThat(profile.majorName()).isEqualTo("Công nghệ thông tin");
        assertThat(profile.admissionYear()).isEqualTo(2026);
        assertThat(profile.academicWarningReason()).isEqualTo("Lý do đã lưu");
        assertThat(profile.targetGpa()).isEqualByComparingTo("3.25");
        verify(studentRepository, never()).save(any());
    }

    @Test
    void missingClassDoesNotInventProgramOrNormalWarningStatus() {
        var student = Student.builder().studentCode("SV99").build();
        student.setId(99L);
        when(studentRepository.findByUserIdAndDeletedFalse(7L)).thenReturn(Optional.of(student));
        var profile = facade.findStudentByUserId(7L);
        assertThat(profile.majorId()).isNull();
        assertThat(profile.cohortId()).isNull();
        assertThat(profile.trainingType()).isNull();
        assertThat(profile.academicWarningStatus()).isNull();
        assertThat(profile.targetGpa()).isNull();
        verifyNoInteractions(referenceData);
    }
}
