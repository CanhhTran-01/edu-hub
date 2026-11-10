package com.eduhub.edu_hub.people.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.application.facade.dto.MajorResponse;
import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.curriculum.domain.model.Major;
import com.eduhub.edu_hub.identity.api.dto.request.CreateStudentAccountRequest;
import com.eduhub.edu_hub.identity.api.dto.response.AccountNotificationResponse;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.request.CreateStudentRequest;
import com.eduhub.edu_hub.people.api.mapper.StudentResponseMapper;
import com.eduhub.edu_hub.people.application.exception.ProfileCreationException;
import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import com.eduhub.edu_hub.people.domain.model.Cohort;
import com.eduhub.edu_hub.people.domain.model.Student;
import com.eduhub.edu_hub.people.domain.repository.AdministrativeClassRepository;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class StudentCreationServiceTest {
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final AdministrativeClassRepository classRepository = mock(AdministrativeClassRepository.class);
    private final CurriculumFacade.Majors curriculumMajors = mock(CurriculumFacade.Majors.class);
    private final StudentCodeGenerator studentCodeGenerator = mock(StudentCodeGenerator.class);
    private final IdentityFacade identityFacade = mock(IdentityFacade.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final Jwt authenticatedJwt = Jwt.withTokenValue("test-token")
            .header("alg", "HS256")
            .subject("academic-admin")
            .build();
    private StudentCreationService studentCreationService;
    private AdministrativeClass administrativeClass;
    private CreateStudentRequest request;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        when(identityFacade.requireCurrentUser(authenticatedJwt))
                .thenReturn(UserInfoResponse.builder().id(7L).build());
        studentCreationService = new StudentCreationService(
                studentRepository,
                classRepository,
                curriculumMajors,
                studentCodeGenerator,
                identityFacade,
                new TransactionTemplate(transactionManager),
                new StudentResponseMapper());
        Cohort cohort = new Cohort();
        cohort.setId(8L);
        cohort.setCohortCode("CT8");
        Major major = Major.builder().majorCode("CNTT").build();
        major.setId(10L);
        administrativeClass = AdministrativeClass.builder()
                .major(major)
                .cohort(cohort)
                .className("CT8A")
                .trainingType(TrainingType.CHINH_QUY)
                .maxCapacity(50)
                .build();
        administrativeClass.setId(1L);
        request = new CreateStudentRequest();
        request.setFullName("Nguyễn Văn An");
        request.setDateOfBirth(LocalDate.of(2006, 8, 21));
        request.setEmail(" Student@Test.COM ");
        request.setCitizenId("012 345 678 901");
        request.setMajorId(10L);
        request.setCohortId(8L);
        request.setAdministrativeClassId(1L);
        request.setReason(" Bổ sung hồ sơ ngoài đợt tuyển sinh ");
    }

    @Test
    void usesSharedCodeAndAccountCreationThenSendsAfterCommit() {
        prepareCreation(true, null);
        var response = studentCreationService.createStudent(authenticatedJwt, request);
        assertThat(response.student().getStudentCode()).isEqualTo("CNTT080101");
        assertThat(response.student().getMajorName()).isEqualTo("Công nghệ thông tin");
        assertThat(response.student().getEmail()).isEqualTo("student@test.com");
        assertThat(response.notificationStatus()).isEqualTo("SENT");
        var accountRequest = ArgumentCaptor.forClass(CreateStudentAccountRequest.class);
        verify(identityFacade).createStudentAccountWithoutNotification(accountRequest.capture());
        assertThat(accountRequest.getValue())
                .isEqualTo(new CreateStudentAccountRequest("CNTT080101", "student@test.com"));
        verify(studentRepository).existsByCitizenIdAndDeletedFalse("012345678901");
        var callOrder = inOrder(studentRepository, transactionManager, identityFacade);
        callOrder.verify(studentRepository).save(any());
        callOrder.verify(transactionManager).commit(any());
        callOrder.verify(identityFacade).sendStudentAccountNotification(20L);
        verify(studentRepository, never()).findByIdAndDeletedFalse(any());
    }

    @Test
    void emailFailureDoesNotAddAProfileWriteOrRetry() {
        prepareCreation(false, "EMAIL_SEND_FAILED");
        var response = studentCreationService.createStudent(authenticatedJwt, request);
        assertThat(response.notificationStatus()).isEqualTo("FAILED");
        assertThat(response.notificationError()).isEqualTo("EMAIL_SEND_FAILED");
        verify(studentRepository, times(1)).save(any());
        verify(identityFacade, times(1)).sendStudentAccountNotification(20L);
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    void unexpectedExceptionInEmailStepStillReturnsCreatedStudent() {
        prepareCreation(true, null);
        when(identityFacade.sendStudentAccountNotification(20L))
                .thenThrow(new IllegalStateException("Unavailable mail"));
        var response = studentCreationService.createStudent(authenticatedJwt, request);
        assertThat(response.student().getId()).isEqualTo(30L);
        assertThat(response.notificationStatus()).isEqualTo("FAILED");
        verify(transactionManager, never()).rollback(any());
        verify(identityFacade, times(1)).sendStudentAccountNotification(20L);
    }

    @Test
    void missingEmailSkipsSendingButStillCreatesAccount() {
        prepareCreation(true, null);
        request.setEmail("   ");
        var response = studentCreationService.createStudent(authenticatedJwt, request);
        assertThat(response.notificationStatus()).isEqualTo("SKIPPED");
        assertThat(response.notificationError()).isEqualTo("ACCOUNT_EMAIL_MISSING");
        assertThat(response.student().getEmail()).isNull();
        verify(identityFacade)
                .createStudentAccountWithoutNotification(new CreateStudentAccountRequest("CNTT080101", null));
        verify(identityFacade, never()).sendStudentAccountNotification(any());
    }

    @Test
    void doesNotSendWhenCommitFails() {
        prepareCreation(true, null);
        doThrow(new TransactionSystemException("Cannot commit"))
                .when(transactionManager)
                .commit(any());
        assertThatThrownBy(() -> studentCreationService.createStudent(authenticatedJwt, request))
                .isInstanceOf(TransactionSystemException.class);
        verify(identityFacade, never()).sendStudentAccountNotification(any());
    }

    @Test
    void profileWriteFailureRollsBackWithoutSending() {
        prepareCreation(true, null);
        doThrow(new DataIntegrityViolationException("Duplicate student"))
                .when(studentRepository)
                .save(any());
        assertThatThrownBy(() -> studentCreationService.createStudent(authenticatedJwt, request))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(transactionManager).rollback(any());
        verify(identityFacade, never()).sendStudentAccountNotification(any());
    }

    @Test
    void fullClassIsRejectedBeforeAllocatingCodeOrAccount() {
        prepareCreation(true, null);
        when(studentRepository.countByAdministrativeClass_IdAndDeletedFalse(1L)).thenReturn(50L);
        assertThatThrownBy(() -> studentCreationService.createStudent(authenticatedJwt, request))
                .isInstanceOfSatisfying(
                        ProfileCreationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CLASS_CAPACITY_EXCEEDED));
        verifyNoInteractions(studentCodeGenerator);
        verify(identityFacade, never()).createStudentAccountWithoutNotification(any());
    }

    @Test
    void rejectsClassFromAnotherCohortBeforeAllocatingCode() {
        prepareCreation(true, null);
        request.setCohortId(9L);
        assertThatThrownBy(() -> studentCreationService.createStudent(authenticatedJwt, request))
                .isInstanceOfSatisfying(
                        ProfileCreationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STUDENT_CLASS_MISMATCH));
        verifyNoInteractions(studentCodeGenerator);
    }

    @Test
    void duplicateCitizenIdReportsTheFieldBeforeCreatingAnything() {
        when(studentRepository.existsByCitizenIdAndDeletedFalse("012345678901")).thenReturn(true);
        assertThatThrownBy(() -> studentCreationService.createStudent(authenticatedJwt, request))
                .isInstanceOfSatisfying(ProfileCreationException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_STUDENT_CITIZEN_ID);
                    assertThat(exception.getField()).isEqualTo("citizenId");
                });
        verifyNoInteractions(classRepository, studentCodeGenerator);
    }

    @Test
    void duplicateGeneratedCodeDoesNotCreateAnAccount() {
        prepareCreation(true, null);
        when(identityFacade.existsByUsername("CNTT080101")).thenReturn(true);
        assertThatThrownBy(() -> studentCreationService.createStudent(authenticatedJwt, request))
                .isInstanceOfSatisfying(
                        ProfileCreationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_STUDENT_CODE));
        verify(identityFacade, never()).createStudentAccountWithoutNotification(any());
    }

    @Test
    void duplicateNameOnlyProducesWarning() {
        prepareCreation(true, null);
        when(studentRepository.existsByFullNameIgnoreCaseAndDateOfBirthAndDeletedFalse(
                        "Nguyễn Văn An", request.getDateOfBirth()))
                .thenReturn(true);
        assertThat(studentCreationService
                        .createStudent(authenticatedJwt, request)
                        .warnings())
                .containsExactly("Có hồ sơ khác trùng họ tên và ngày sinh; cần kiểm tra thủ công");
    }

    @Test
    void principalIsRequiredForDirectServiceCalls() {
        assertThatThrownBy(() -> studentCreationService.createStudent(null, request))
                .isInstanceOfSatisfying(
                        ProfileCreationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        verifyNoInteractions(identityFacade, transactionManager);
    }

    private void prepareCreation(boolean emailSent, String emailError) {
        when(classRepository.findByIdForStudentCodeUpdate(1L)).thenReturn(Optional.of(administrativeClass));
        when(curriculumMajors.requireMajor(10L)).thenReturn(new MajorResponse(10L, "CNTT", "Công nghệ thông tin"));
        when(studentCodeGenerator.allocateNextStudentSequence(administrativeClass))
                .thenReturn(1);
        when(studentCodeGenerator.generateStudentCode(administrativeClass, 1)).thenReturn("CNTT080101");
        when(identityFacade.createStudentAccountWithoutNotification(any())).thenReturn(20L);
        when(identityFacade.sendStudentAccountNotification(20L))
                .thenReturn(new AccountNotificationResponse(20L, emailSent, emailError));
        when(studentRepository.save(any())).thenAnswer(invocation -> {
            Student savedStudent = invocation.getArgument(0);
            savedStudent.setId(30L);
            return savedStudent;
        });
    }
}
