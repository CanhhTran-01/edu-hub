package com.eduhub.edu_hub.people.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.domain.model.Major;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.identity.application.result.CreateStudentAccountResult;
import com.eduhub.edu_hub.people.api.dto.request.AdmissionConfirmRequest;
import com.eduhub.edu_hub.people.application.admission.AdmissionBatchState;
import com.eduhub.edu_hub.people.application.admission.AdmissionCandidate;
import com.eduhub.edu_hub.people.application.admission.AdmissionPreviewStore;
import com.eduhub.edu_hub.people.application.admission.ClassAssignmentStrategy;
import com.eduhub.edu_hub.people.application.admission.StudentAdmissionSpreadsheetParser;
import com.eduhub.edu_hub.people.application.code.StudentCodeGenerator;
import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import com.eduhub.edu_hub.people.domain.model.Student;
import com.eduhub.edu_hub.people.domain.repository.AdministrativeClassRepository;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.exception.BaseException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class StudentAdmissionServiceTest {
    @Mock StudentAdmissionSpreadsheetParser parser;
    @Mock ClassAssignmentStrategy strategy;
    @Mock StudentRepository students;
    @Mock AdministrativeClassRepository classes;
    @Mock StudentCodeGenerator generator;
    @Mock IdentityFacade identity;
    @Mock CurriculumFacade.Majors majors;
    private AdmissionPreviewStore store;
    private StudentAdmissionService service;

    @BeforeEach
    void setUp() {
        store = new AdmissionPreviewStore();
        service = new StudentAdmissionService(parser, strategy, store, students, classes, generator, identity,
                majors);
    }

    @Test
    void confirmCreatesStudentThroughIdentityAndDoesNotRepeat() {
        AdmissionBatchState batch = batch(); store.put(batch);
        when(classes.findByIdForStudentCodeUpdate(1L)).thenReturn(Optional.of(batch.getClasses().get(0)));
        when(generator.nextSequence(any())).thenReturn(1);
        when(generator.generate(any(), anyInt())).thenReturn("CT080101");
        when(identity.createStudentAccount(any())).thenReturn(new CreateStudentAccountResult(10L, true, null));
        var result = service.confirm(batch.getId(), new AdmissionConfirmRequest("admin", true));
        assertThat(result.createdStudents()).isEqualTo(1);
        verify(identity).createStudentAccount(any());
        verify(students).save(any(Student.class));
        assertThatThrownBy(() -> service.confirm(batch.getId(), new AdmissionConfirmRequest("admin", true)))
                .isInstanceOf(BaseException.class);
    }

    @Test
    void emailFailureIsReportedWithoutDiscardingStudent() {
        AdmissionBatchState batch = batch(); store.put(batch);
        when(classes.findByIdForStudentCodeUpdate(1L)).thenReturn(Optional.of(batch.getClasses().get(0)));
        when(generator.nextSequence(any())).thenReturn(1);
        when(generator.generate(any(), anyInt())).thenReturn("CT080101");
        when(identity.createStudentAccount(any())).thenReturn(new CreateStudentAccountResult(10L, false, "EMAIL_SEND_FAILED"));
        var result = service.confirm(batch.getId(), new AdmissionConfirmRequest("admin", true));
        assertThat(result.emailFailures()).containsExactly("HS01");
        verify(students).save(any(Student.class));
    }

    @Test
    void capacityWarningRequiresExplicitAcceptanceAndWritesNothing() {
        AdmissionBatchState batch = batch(); batch.setCapacityWarnings(List.of("warning")); store.put(batch);
        assertThatThrownBy(() -> service.confirm(batch.getId(), new AdmissionConfirmRequest("admin", false)))
                .isInstanceOf(BaseException.class);
        verify(students, never()).save(any());
        verify(identity, never()).createStudentAccount(any());
    }

    @Test
    void previewKeepsValidDuplicatePeerAndDoesNotPersist() throws Exception {
        AdmissionCandidate first = candidate("HS01", "one@example.test");
        AdmissionCandidate duplicate = candidate("HS01", "two@example.test");
        when(parser.parse(any())).thenReturn(new com.eduhub.edu_hub.people.application.admission.AdmissionParseResult(
                2, List.of(first, duplicate), List.of()));
        when(students.existsByAdmissionApplicationCodeAndDeletedFalse(any())).thenReturn(false);
        when(students.existsByEmailIgnoreCaseAndDeletedFalse(any())).thenReturn(false);
        AdministrativeClass target = batch().getClasses().get(0);
        var major = Major.builder().majorCode("ATTT").build();
        major.setId(42L);
        target.setMajor(major);
        var cohort = new com.eduhub.edu_hub.people.domain.model.Cohort(); cohort.setCohortCode("K20"); target.setCohort(cohort);
        target.setTrainingType(com.eduhub.edu_hub.shared.enums.TrainingType.CHINH_QUY);
        when(majors.findMajorByCode(any())).thenReturn(Optional.of(
                new com.eduhub.edu_hub.curriculum.application.facade.dto.MajorResponse(42L, "ATTT", "An toan thong tin")));
        when(classes.findByMajor_IdAndCohort_CohortCodeIgnoreCaseAndTrainingTypeAndDeletedFalse(
                any(), any(), any())).thenReturn(List.of(target));
        when(students.findAllByAdministrativeClass_IdAndDeletedFalse(1L)).thenReturn(List.of());
        doAnswer(invocation -> {
            List<AdmissionCandidate> values = invocation.getArgument(0);
            values.forEach(value -> value.setAssignedClassId(1L));
            return new ClassAssignmentStrategy.AssignmentResult(List.of(), List.of());
        }).when(strategy).assign(any(), any());

        var result = service.preview(new MockMultipartFile("file", "students.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] {1}));

        assertThat(result.validRows()).isEqualTo(1);
        assertThat(result.errors()).anyMatch(error -> error.reason().contains("Trùng mã hồ sơ"));
        verify(students, never()).save(any());
        verify(identity, never()).createStudentAccount(any());
    }

    private AdmissionBatchState batch() {
        AdministrativeClass administrativeClass = new AdministrativeClass(); administrativeClass.setId(1L);
        administrativeClass.setClassName("L01");
        var major = Major.builder().majorCode("CNTT").build();
        major.setId(1L);
        administrativeClass.setMajor(major);
        var cohort = new com.eduhub.edu_hub.people.domain.model.Cohort(); cohort.setCohortCode("CT8");
        administrativeClass.setCohort(cohort); administrativeClass.setMinCapacity(0); administrativeClass.setMaxCapacity(50);
        AdmissionCandidate candidate = new AdmissionCandidate(); candidate.setApplicationCode("HS01");
        candidate.setFullName("Nguyễn An"); candidate.setEmail("an@example.test");
        candidate.setEntranceScore(new BigDecimal("25")); candidate.setAssignedClassId(1L);
        return new AdmissionBatchState(UUID.randomUUID(), "students.xlsx", 1, new ArrayList<>(List.of(candidate)),
                new ArrayList<>(), List.of(administrativeClass), List.of(), new ArrayList<>());
    }

    private AdmissionCandidate candidate(String code, String email) {
        AdmissionCandidate value = new AdmissionCandidate(); value.setApplicationCode(code); value.setFullName("Nguyễn An");
        value.setEmail(email); value.setEntranceScore(new BigDecimal("25")); value.setMajorCode("ATTT");
        value.setCohortCode("K20"); value.setTrainingType("CHINH_QUY"); return value;
    }
}
