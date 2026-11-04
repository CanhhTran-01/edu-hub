package com.eduhub.edu_hub.people.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.eduhub.edu_hub.identity.application.*;
import com.eduhub.edu_hub.people.api.dto.*;
import com.eduhub.edu_hub.people.domain.model.*;
import com.eduhub.edu_hub.people.domain.repository.*;
import com.eduhub.edu_hub.shared.audit.AuditLogRepository;
import com.eduhub.edu_hub.shared.exception.BaseException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class LecturerCreationServiceTest {
    LecturerRepository lecturers = mock(LecturerRepository.class);
    FacultyRepository faculties = mock(FacultyRepository.class);
    LecturerCodeGenerator codes = mock(LecturerCodeGenerator.class);
    IdentityFacade identity = mock(IdentityFacade.class);
    AuditLogRepository audits = mock(AuditLogRepository.class);
    LecturerApplicationService queries = mock(LecturerApplicationService.class);
    LecturerCreationService service;
    CreateLecturerRequest request;

    @BeforeEach void setup() {
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new LecturerCreationService(lecturers, faculties, codes, identity, audits,
                new TransactionTemplate(manager), queries);
        when(identity.requireAcademicAdmin("token")).thenReturn("admin");
        request = new CreateLecturerRequest();
        request.setFullName(" Nguyen Van A "); request.setEmail(" A@Example.test ");
        request.setDateOfBirth(LocalDate.of(1980, 1, 1)); request.setFacultyId(1L);
        request.setReason(" Bo sung giang vien "); request.setCitizenId(" 123 456 ");
    }

    @Test void createsAccountAndKeepsDataWhenEmailFails() {
        Faculty faculty = new Faculty(); faculty.setId(1L);
        when(faculties.findByIdAndDeletedFalseAndActiveTrue(1L)).thenReturn(Optional.of(faculty));
        when(codes.generate()).thenReturn("GV000001");
        when(identity.createLecturerAccountWithoutNotification(any())).thenReturn(2L);
        Lecturer[] saved = new Lecturer[1];
        when(lecturers.saveAndFlush(any())).thenAnswer(call -> {
            saved[0] = call.getArgument(0); saved[0].setId(3L); return saved[0];
        });
        when(lecturers.findByIdAndDeletedFalse(3L)).thenAnswer(call -> Optional.of(saved[0]));
        when(identity.sendLecturerAccountNotification(eq(2L), anyString()))
                .thenReturn(new CreateStudentAccountResult(2L, false, "EMAIL_SEND_FAILED"));
        var response = service.create(request, "token");
        assertThat(response.notificationStatus()).isEqualTo("FAILED");
        assertThat(saved[0].getEmail()).isEqualTo("a@example.test");
        assertThat(saved[0].getCitizenId()).isEqualTo("123456");
        var command = ArgumentCaptor.forClass(CreateLecturerAccountCommand.class);
        verify(identity).createLecturerAccountWithoutNotification(command.capture());
        assertThat(command.getValue().username()).isEqualTo("GV000001");
        var logs = ArgumentCaptor.forClass(com.eduhub.edu_hub.shared.audit.AuditLog.class);
        verify(audits, times(2)).save(logs.capture());
        assertThat(logs.getAllValues()).allSatisfy(log -> {
            assertThat(log.getActor()).isEqualTo("admin");
            assertThat(log.getDetails()).doesNotContain("12345");
        });
        assertThat(logs.getAllValues().get(0).getDetails()).contains("reason=Bo sung giang vien");
    }

    @Test void rejectsDuplicateEmailBeforeCreatingAnything() {
        when(lecturers.existsByEmailIgnoreCaseAndDeletedFalse("a@example.test")).thenReturn(true);
        assertThatThrownBy(() -> service.create(request, "token")).isInstanceOf(BaseException.class);
        verifyNoInteractions(codes); verify(identity, never()).createLecturerAccountWithoutNotification(any());
    }

    @Test void rejectsMissingOrInactiveFaculty() {
        when(faculties.findByIdAndDeletedFalseAndActiveTrue(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(request, "token")).isInstanceOf(BaseException.class);
        verifyNoInteractions(codes);
    }

    @Test void rejectsBlankReason() {
        request.setReason("   ");
        assertThatThrownBy(() -> service.create(request, "token")).isInstanceOf(BaseException.class);
        verifyNoInteractions(codes);
    }

    @Test void resendUsesExistingAccountWithoutGeneratingCode() {
        Lecturer lecturer = new Lecturer(); lecturer.setId(3L); lecturer.setUserId(2L); lecturer.setLecturerCode("GV000001");
        when(lecturers.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(lecturer));
        when(identity.sendLecturerAccountNotification(eq(2L), anyString()))
                .thenReturn(new CreateStudentAccountResult(2L, true, null));
        assertThat(service.resend(3L, "token").notificationStatus()).isEqualTo("SENT");
        verifyNoInteractions(codes);
        verify(identity, never()).createLecturerAccountWithoutNotification(any());
    }
}
