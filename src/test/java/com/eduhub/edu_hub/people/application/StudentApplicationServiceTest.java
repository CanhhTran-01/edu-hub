package com.eduhub.edu_hub.people.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.application.facade.dto.MajorSimpleResponse;
import com.eduhub.edu_hub.curriculum.domain.model.Major;
import com.eduhub.edu_hub.people.api.dto.request.UpdateStudentRequest;
import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import com.eduhub.edu_hub.people.domain.model.Cohort;
import com.eduhub.edu_hub.people.domain.model.Student;
import com.eduhub.edu_hub.people.domain.repository.AdministrativeClassRepository;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.enums.TrainingType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentApplicationServiceTest {
    @Mock
    private StudentRepository students;

    @Mock
    private AdministrativeClassRepository classes;

    @Mock
    private CurriculumFacade.ReferenceData curriculum;

    @InjectMocks
    private StudentApplicationService service;

    @Test
    void warningReasonIsStoredAndClearedTogetherWithWarningStatus() {
        var student = new Student();
        student.setId(1L);
        when(students.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(student));
        when(students.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new UpdateStudentRequest();
        request.setAcademicWarningStatus(" LEVEL_1 ");
        request.setAcademicWarningReason(" GPA dưới ngưỡng ");

        var response = service.update(1L, request);
        assertThat(response.getAcademicWarningStatus()).isEqualTo("LEVEL_1");
        assertThat(response.getAcademicWarningReason()).isEqualTo("GPA dưới ngưỡng");

        request = new UpdateStudentRequest();
        request.setAcademicWarningStatus(" ");
        response = service.update(1L, request);
        assertThat(response.getAcademicWarningStatus()).isNull();
        assertThat(response.getAcademicWarningReason()).isNull();
    }

    @Test
    void profileGetsTheCurrentMajorNameThroughTheCurriculumFacade() {
        var student = new Student();
        var administrativeClass = new AdministrativeClass();
        var major = Major.builder().build();
        major.setId(2L);
        administrativeClass.setMajor(major);
        administrativeClass.setCohort(new Cohort());
        administrativeClass.setTrainingType(TrainingType.CHINH_QUY);
        student.setAdministrativeClass(administrativeClass);
        when(students.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(student));
        when(curriculum.findMajorsByIds(Set.of(2L)))
                .thenReturn(List.of(new MajorSimpleResponse(2L, "ATTT", "Tên ngành từ Curriculum", null)));

        assertThat(service.findById(1L).getMajorName()).isEqualTo("Tên ngành từ Curriculum");
    }
}
