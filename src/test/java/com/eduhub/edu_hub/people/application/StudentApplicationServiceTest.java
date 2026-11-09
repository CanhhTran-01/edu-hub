package com.eduhub.edu_hub.people.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.application.facade.dto.MajorSimpleResponse;
import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.curriculum.domain.model.Major;
import com.eduhub.edu_hub.identity.api.dto.response.UserAccountEmailResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.request.StudentSearchRequest;
import com.eduhub.edu_hub.people.api.mapper.StudentResponseMapper;
import com.eduhub.edu_hub.people.application.service.StudentApplicationService;
import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import com.eduhub.edu_hub.people.domain.model.Cohort;
import com.eduhub.edu_hub.people.domain.model.Student;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class StudentApplicationServiceTest {
    @Mock
    private StudentRepository students;

    @Mock
    private CurriculumFacade.ReferenceData curriculum;

    @Mock
    private IdentityFacade identityFacade;

    @Spy
    private StudentResponseMapper studentResponseMapper = new StudentResponseMapper();

    @InjectMocks
    private StudentApplicationService service;

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

    @Test
    void listReadsAccountEmailsInOneBatchAndKeepsProfilesWithoutAccounts() {
        Student firstStudent = new Student();
        firstStudent.setId(1L);
        firstStudent.setUserId(101L);
        Student secondStudent = new Student();
        secondStudent.setId(2L);
        secondStudent.setUserId(102L);
        Student unlinkedStudent = new Student();
        unlinkedStudent.setId(3L);
        when(students.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(firstStudent, secondStudent, unlinkedStudent)));
        when(identityFacade.findAccountEmailsByUserIds(Set.of(101L, 102L)))
                .thenReturn(List.of(
                        new UserAccountEmailResponse(101L, "shared@example.com"),
                        new UserAccountEmailResponse(102L, "shared@example.com")));

        var response = service.search(new StudentSearchRequest(), 0, 20);

        assertThat(response.getContent())
                .extracting(student -> student.getEmail())
                .containsExactly("shared@example.com", "shared@example.com", null);
        verify(identityFacade, times(1)).findAccountEmailsByUserIds(Set.of(101L, 102L));
    }

    @Test
    void detailReadsEmailFromLinkedUserEvenWhenNoOtherProfileFieldsChange() {
        Student student = new Student();
        student.setId(1L);
        student.setUserId(101L);
        when(students.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(student));
        when(identityFacade.findAccountEmailsByUserIds(Set.of(101L)))
                .thenReturn(List.of(new UserAccountEmailResponse(101L, "account@example.com")));

        assertThat(service.findById(1L).getEmail()).isEqualTo("account@example.com");
    }
}
