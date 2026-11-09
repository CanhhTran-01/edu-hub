package com.eduhub.edu_hub.people.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eduhub.edu_hub.identity.api.dto.request.CreateLecturerAccountRequest;
import com.eduhub.edu_hub.identity.api.dto.response.AccountNotificationResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.request.CreateLecturerRequest;
import com.eduhub.edu_hub.people.api.dto.response.LecturerResponse;
import com.eduhub.edu_hub.people.application.code.LecturerCodeGenerator;
import com.eduhub.edu_hub.people.application.service.LecturerAccountNotificationService;
import com.eduhub.edu_hub.people.application.service.LecturerApplicationService;
import com.eduhub.edu_hub.people.application.service.LecturerCreationService;
import com.eduhub.edu_hub.people.domain.enums.LecturerStatus;
import com.eduhub.edu_hub.people.domain.model.Faculty;
import com.eduhub.edu_hub.people.domain.model.Lecturer;
import com.eduhub.edu_hub.people.domain.repository.FacultyRepository;
import com.eduhub.edu_hub.people.domain.repository.LecturerRepository;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class LecturerCreationServiceTest {
    private final LecturerRepository lecturers = mock(LecturerRepository.class);
    private final FacultyRepository faculties = mock(FacultyRepository.class);
    private final LecturerCodeGenerator codes = mock(LecturerCodeGenerator.class);
    private final IdentityFacade identity = mock(IdentityFacade.class);
    private final LecturerApplicationService queries = mock(LecturerApplicationService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

    private LecturerCreationService service;
    private CreateLecturerRequest request;
    private Lecturer savedLecturer;

    @BeforeEach
    void setup() {
        when(transactionManager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        var transactions = new TransactionTemplate(transactionManager);
        var notifications = new LecturerAccountNotificationService(lecturers, identity);
        service = new LecturerCreationService(
                lecturers, faculties, codes, identity, transactions, queries, notifications);

        request = new CreateLecturerRequest();
        request.setFullName(" Nguyen Van A ");
        request.setEmail(" A@Example.test ");
        request.setDateOfBirth(LocalDate.of(1980, 1, 1));
        request.setFacultyId(1L);
        request.setReason(" Bo sung giang vien ");
        request.setCitizenId(" 123 456 ");
        request.setPhone(" 0901234567 ");
    }

    @Test
    void createsAccountAndKeepsDataWhenEmailFails() {
        prepareCreation(false, "EMAIL_SEND_FAILED");

        var response = service.create(request);

        assertThat(response.notificationStatus()).isEqualTo("FAILED");
        assertThat(response.notificationError()).isEqualTo("EMAIL_SEND_FAILED");
        assertThat(response.lecturer().getId()).isEqualTo(3L);
        assertThat(savedLecturer.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(response.lecturer().getEmail()).isEqualTo("a@example.test");
        assertThat(savedLecturer.getCitizenId()).isEqualTo("123456");
        assertThat(savedLecturer.getPhone()).isEqualTo("0901234567");
        assertThat(savedLecturer.getStatus()).isEqualTo(LecturerStatus.ACTIVE);

        var command = ArgumentCaptor.forClass(CreateLecturerAccountRequest.class);
        verify(identity).createLecturerAccountWithoutNotification(command.capture());
        assertThat(command.getValue().username()).isEqualTo("GV000001");
        assertThat(command.getValue().email()).isEqualTo("a@example.test");
        verify(lecturers, never()).save(any());
    }

    @Test
    void commitsCreationBeforeSendingAndReturnsNotificationWithoutAnotherProfileWrite() {
        prepareCreation(true, null);

        var response = service.create(request);

        var order = inOrder(lecturers, identity, transactionManager, queries);
        order.verify(lecturers).saveAndFlush(any(Lecturer.class));
        order.verify(transactionManager).commit(any());
        order.verify(identity).sendLecturerAccountNotification(2L);
        order.verify(queries).findById(3L);
        verify(transactionManager, times(1)).commit(any());
        verify(lecturers, never()).save(any());
        assertThat(response.notificationStatus()).isEqualTo("SENT");
    }

    @Test
    void doesNotSendNotificationWhenCreationCommitFails() {
        prepareCreation(true, null);
        doThrow(new TransactionSystemException("Cannot commit lecturer"))
                .when(transactionManager)
                .commit(any());

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(TransactionSystemException.class);

        verify(identity, never()).sendLecturerAccountNotification(any());
        verifyNoInteractions(queries);
    }

    @Test
    void rollsBackCreationAndDoesNotSendWhenSavingFails() {
        prepareCreation(true, null);
        doThrow(new DataIntegrityViolationException("Duplicate lecturer"))
                .when(lecturers)
                .saveAndFlush(any());

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(DataIntegrityViolationException.class);

        verify(transactionManager).rollback(any());
        verify(identity, never()).sendLecturerAccountNotification(any());
        verifyNoInteractions(queries);
    }

    @Test
    void duplicateNameAndBirthDateOnlyProduceWarning() {
        prepareCreation(true, null);
        when(lecturers.existsByFullNameIgnoreCaseAndDateOfBirthAndDeletedFalse(
                        "Nguyen Van A", request.getDateOfBirth()))
                .thenReturn(true);

        var response = service.create(request);

        assertThat(response.warnings()).containsExactly("Trùng họ tên và ngày sinh; cần kiểm tra thủ công");
        verify(lecturers).saveAndFlush(any(Lecturer.class));
        verify(identity).sendLecturerAccountNotification(2L);
    }

    @Test
    void blankCitizenIdIsStoredAsNullWithoutDuplicateLookup() {
        prepareCreation(true, null);
        request.setCitizenId("  \t ");

        service.create(request);

        assertThat(savedLecturer.getCitizenId()).isNull();
        verify(lecturers, never()).existsByCitizenIdAndDeletedFalse(anyString());
    }

    @Test
    void rejectsDuplicateEmailBeforeCreatingAnything() {
        when(identity.findUserIdsByEmail("a@example.test")).thenReturn(Set.of(90L));
        when(lecturers.existsByUserIdInAndDeletedFalse(Set.of(90L))).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_LECTURER_EMAIL));

        verifyNoInteractions(codes);
        verify(identity, never()).createLecturerAccountWithoutNotification(any());
    }

    @Test
    void emailUsedOnlyByAStudentAccountDoesNotBlockLecturerCreation() {
        prepareCreation(true, null);
        when(identity.findUserIdsByEmail("a@example.test")).thenReturn(Set.of(90L));
        when(lecturers.existsByUserIdInAndDeletedFalse(Set.of(90L))).thenReturn(false);

        service.create(request);

        verify(identity).createLecturerAccountWithoutNotification(any());
        verify(lecturers).existsByUserIdInAndDeletedFalse(Set.of(90L));
    }

    @Test
    void rejectsDuplicateCitizenIdBeforeCreatingAnything() {
        when(lecturers.existsByCitizenIdAndDeletedFalse("123456")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_LECTURER_CITIZEN_ID));

        verifyNoInteractions(faculties, codes);
        verify(identity, never()).createLecturerAccountWithoutNotification(any());
    }

    @Test
    void rejectsMissingOrInactiveFaculty() {
        when(faculties.findByIdAndDeletedFalseAndActiveTrue(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.FACULTY_NOT_ACTIVE));

        verifyNoInteractions(codes);
    }

    @Test
    void rejectsBlankReason() {
        request.setReason("   ");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));

        verifyNoInteractions(lecturers, faculties, codes);
    }

    @Test
    void rejectsReasonLongerThanFiveHundredCharacters() {
        request.setReason("x".repeat(501));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));

        verifyNoInteractions(lecturers, faculties, codes);
    }

    @Test
    void resendUsesExistingAccountWithoutGeneratingCode() {
        var lecturer = existingLecturer();
        when(lecturers.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(lecturer));
        when(identity.sendLecturerAccountNotification(2L)).thenReturn(new AccountNotificationResponse(2L, true, null));

        var response = service.resend(3L);

        assertThat(response.lecturerId()).isEqualTo(3L);
        assertThat(response.lecturerCode()).isEqualTo("GV000001");
        assertThat(response.notificationStatus()).isEqualTo("SENT");
        verify(lecturers, never()).save(any());
        verifyNoInteractions(transactionManager);
        verifyNoInteractions(codes, faculties, queries);
        verify(identity, never()).createLecturerAccountWithoutNotification(any());
    }

    @Test
    void rejectsResendForMissingLecturer() {
        when(lecturers.findByIdAndDeletedFalse(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resend(3L))
                .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.LECTURER_NOT_FOUND));

        verify(identity, never()).sendLecturerAccountNotification(any());
        verifyNoInteractions(transactionManager, codes);
    }

    @Test
    void rejectsResendWhenAccountIsMissing() {
        var lecturer = existingLecturer();
        lecturer.setUserId(null);
        when(lecturers.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(lecturer));

        assertThatThrownBy(() -> service.resend(3L))
                .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));

        verify(identity, never()).sendLecturerAccountNotification(any());
        verifyNoInteractions(transactionManager, codes);
    }

    private void prepareCreation(boolean emailSent, String emailError) {
        Faculty faculty = new Faculty();
        faculty.setId(1L);
        when(faculties.findByIdAndDeletedFalseAndActiveTrue(1L)).thenReturn(Optional.of(faculty));
        when(codes.generate()).thenReturn("GV000001");
        when(identity.createLecturerAccountWithoutNotification(any())).thenReturn(2L);
        when(lecturers.saveAndFlush(any())).thenAnswer(call -> {
            savedLecturer = call.getArgument(0);
            savedLecturer.setId(3L);
            return savedLecturer;
        });
        when(lecturers.findByIdAndDeletedFalse(3L)).thenAnswer(call -> Optional.of(savedLecturer));
        when(identity.sendLecturerAccountNotification(eq(2L)))
                .thenReturn(new AccountNotificationResponse(2L, emailSent, emailError));
        when(queries.findById(3L))
                .thenReturn(LecturerResponse.builder()
                        .id(3L)
                        .lecturerCode("GV000001")
                        .email("a@example.test")
                        .build());
    }

    private Lecturer existingLecturer() {
        Lecturer lecturer = new Lecturer();
        lecturer.setId(3L);
        lecturer.setUserId(2L);
        lecturer.setLecturerCode("GV000001");
        return lecturer;
    }
}
