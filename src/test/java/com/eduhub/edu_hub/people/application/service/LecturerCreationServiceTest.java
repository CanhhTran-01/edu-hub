package com.eduhub.edu_hub.people.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.exception.TeachingSubjectException;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.identity.api.dto.request.CreateLecturerAccountRequest;
import com.eduhub.edu_hub.identity.api.dto.response.AccountNotificationResponse;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.request.CreateLecturerRequest;
import com.eduhub.edu_hub.people.api.mapper.LecturerResponseMapper;
import com.eduhub.edu_hub.people.application.exception.ProfileCreationException;
import com.eduhub.edu_hub.people.domain.model.Faculty;
import com.eduhub.edu_hub.people.domain.model.Lecturer;
import com.eduhub.edu_hub.people.domain.model.LecturerTeachingSubject;
import com.eduhub.edu_hub.people.domain.repository.*;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class LecturerCreationServiceTest {
    private final LecturerRepository lecturerRepository = mock(LecturerRepository.class);
    private final FacultyRepository facultyRepository = mock(FacultyRepository.class);
    private final LecturerCodeGenerator lecturerCodeGenerator = mock(LecturerCodeGenerator.class);
    private final IdentityFacade identityFacade = mock(IdentityFacade.class);
    private final LecturerTeachingSubjectRepository teachingSubjectRepository =
            mock(LecturerTeachingSubjectRepository.class);
    private final CurriculumFacade.TeachingSubjects curriculumTeachingSubjects =
            mock(CurriculumFacade.TeachingSubjects.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final Jwt authenticatedJwt = Jwt.withTokenValue("test-token")
            .header("alg", "HS256")
            .subject("academic-admin")
            .build();
    private LecturerCreationService lecturerCreationService;
    private CreateLecturerRequest request;
    private Lecturer savedLecturer;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        when(identityFacade.requireCurrentUser(authenticatedJwt))
                .thenReturn(UserInfoResponse.builder().id(7L).build());
        lecturerCreationService = new LecturerCreationService(
                lecturerRepository,
                facultyRepository,
                lecturerCodeGenerator,
                identityFacade,
                new TransactionTemplate(transactionManager),
                teachingSubjectRepository,
                curriculumTeachingSubjects,
                new LecturerResponseMapper());
        request = new CreateLecturerRequest();
        request.setFullName(" Nguyễn Văn An ");
        request.setEmail(" A@Example.test ");
        request.setDateOfBirth(LocalDate.of(1980, 1, 1));
        request.setFacultyId(1L);
        request.setRecruitmentYear(2026);
        request.setTeachingSubjectIds(List.of(10L, 11L));
        request.setReason(" Bổ sung giảng viên ");
        request.setCitizenId(" 012 345 678 901 ");
        request.setPhone(" 0901234567 ");
    }

    @Test
    void createsProfileAndTeachingSubjectsWhileReturningEmailFailure() {
        prepareCreation(false, "EMAIL_SEND_FAILED");
        var response = lecturerCreationService.createLecturer(authenticatedJwt, request);
        assertThat(response.notificationStatus()).isEqualTo("FAILED");
        assertThat(response.notificationError()).isEqualTo("EMAIL_SEND_FAILED");
        assertThat(response.lecturer().getLecturerCode()).isEqualTo("CNTT26001");
        assertThat(response.lecturer().getRecruitmentYear()).isEqualTo(2026);
        assertThat(response.lecturer().getTeachingSubjects())
                .extracting(SubjectSimpleResponse::getId)
                .containsExactly(10L, 11L);
        assertThat(savedLecturer.getFullName()).isEqualTo("Nguyễn Văn An");
        assertThat(savedLecturer.getCitizenId()).isEqualTo("012345678901");
        assertThat(savedLecturer.getPhone()).isEqualTo("0901234567");
        var accountRequest = ArgumentCaptor.forClass(CreateLecturerAccountRequest.class);
        verify(identityFacade).createLecturerAccountWithoutNotification(accountRequest.capture());
        assertThat(accountRequest.getValue())
                .isEqualTo(new CreateLecturerAccountRequest("CNTT26001", "a@example.test"));
        verify(lecturerRepository, never()).findByIdAndDeletedFalse(any());
        verify(lecturerRepository, never()).save(any());
    }

    @Test
    void commitsAllCreationWritesBeforeSendingExactlyOnce() {
        prepareCreation(true, null);
        var response = lecturerCreationService.createLecturer(authenticatedJwt, request);
        var callOrder = inOrder(lecturerRepository, teachingSubjectRepository, transactionManager, identityFacade);
        callOrder.verify(lecturerRepository).saveAndFlush(any());
        callOrder.verify(teachingSubjectRepository).saveAll(any());
        callOrder.verify(transactionManager).commit(any());
        callOrder.verify(identityFacade).sendLecturerAccountNotification(2L);
        verify(transactionManager, times(1)).commit(any());
        verify(transactionManager, never()).rollback(any());
        verify(identityFacade, times(1)).sendLecturerAccountNotification(2L);
        assertThat(response.notificationStatus()).isEqualTo("SENT");
    }

    @Test
    void containsUnexpectedEmailExceptionAfterCommitWithoutRetryingOrRollingBackCreation() {
        prepareCreation(true, null);
        when(identityFacade.sendLecturerAccountNotification(2L))
                .thenThrow(new IllegalStateException("Email unavailable"));
        var response = lecturerCreationService.createLecturer(authenticatedJwt, request);
        assertThat(response.notificationStatus()).isEqualTo("FAILED");
        verify(identityFacade, times(1)).sendLecturerAccountNotification(2L);
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    void doesNotSendWhenCommitFails() {
        prepareCreation(true, null);
        doThrow(new TransactionSystemException("Cannot commit"))
                .when(transactionManager)
                .commit(any());
        assertThatThrownBy(() -> lecturerCreationService.createLecturer(authenticatedJwt, request))
                .isInstanceOf(TransactionSystemException.class);
        verify(identityFacade, never()).sendLecturerAccountNotification(any());
    }

    @Test
    void rollsBackAndDoesNotSendWhenProfileWriteFails() {
        prepareCreation(true, null);
        doThrow(new DataIntegrityViolationException("Duplicate lecturer"))
                .when(lecturerRepository)
                .saveAndFlush(any());
        assertThatThrownBy(() -> lecturerCreationService.createLecturer(authenticatedJwt, request))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(transactionManager).rollback(any());
        verifyNoInteractions(teachingSubjectRepository);
        verify(identityFacade, never()).sendLecturerAccountNotification(any());
    }

    @Test
    void rollsBackAndDoesNotSendWhenTeachingLinksCannotBeSaved() {
        prepareCreation(true, null);
        when(teachingSubjectRepository.saveAll(any()))
                .thenThrow(new DataIntegrityViolationException("Invalid subject link"));
        assertThatThrownBy(() -> lecturerCreationService.createLecturer(authenticatedJwt, request))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(transactionManager).rollback(any());
        verify(identityFacade, never()).sendLecturerAccountNotification(any());
    }

    @Test
    void rejectsUnavailableSubjectsBeforeAllocatingCodeOrCreatingAccount() {
        prepareCreation(true, null);
        when(curriculumTeachingSubjects.requireActiveTeachingSubjects(Set.of(10L, 11L)))
                .thenThrow(new TeachingSubjectException(ErrorCode.SUBJECT_NOT_TEACHABLE));
        assertThatThrownBy(() -> lecturerCreationService.createLecturer(authenticatedJwt, request))
                .isInstanceOf(TeachingSubjectException.class);
        verifyNoInteractions(lecturerCodeGenerator, teachingSubjectRepository);
        verify(identityFacade, never()).createLecturerAccountWithoutNotification(any());
    }

    @Test
    void recordsRepeatedSubjectIdsOnlyOnce() {
        prepareCreation(true, null);
        request.setTeachingSubjectIds(List.of(10L, 10L, 11L));
        lecturerCreationService.createLecturer(authenticatedJwt, request);
        ArgumentCaptor<List<LecturerTeachingSubject>> teachingLinks = ArgumentCaptor.forClass(List.class);
        verify(teachingSubjectRepository).saveAll(teachingLinks.capture());
        assertThat(teachingLinks.getValue())
                .extracting(LecturerTeachingSubject::getSubjectId)
                .containsExactly(10L, 11L);
        assertThat(teachingLinks.getValue())
                .allMatch(link -> link.getLecturerId().equals(3L));
    }

    @Test
    void duplicateNameAndBirthDateOnlyProduceWarning() {
        prepareCreation(true, null);
        when(lecturerRepository.existsByFullNameIgnoreCaseAndDateOfBirthAndDeletedFalse(
                        "Nguyễn Văn An", request.getDateOfBirth()))
                .thenReturn(true);
        assertThat(lecturerCreationService
                        .createLecturer(authenticatedJwt, request)
                        .warnings())
                .containsExactly("Trùng họ tên và ngày sinh; cần kiểm tra thủ công");
    }

    @Test
    void blankCitizenIdRemainsOptional() {
        prepareCreation(true, null);
        request.setCitizenId("  \t ");
        lecturerCreationService.createLecturer(authenticatedJwt, request);
        assertThat(savedLecturer.getCitizenId()).isNull();
        verify(lecturerRepository, never()).existsByCitizenIdAndDeletedFalse(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456", "01234567890A", "0123456789012"})
    void rejectsMalformedCitizenIdBeforeCreatingAnything(String citizenId) {
        request.setCitizenId(citizenId);
        assertThatThrownBy(() -> lecturerCreationService.createLecturer(authenticatedJwt, request))
                .isInstanceOfSatisfying(ProfileCreationException.class, exception -> assertThat(exception.getField())
                        .isEqualTo("citizenId"));
        verifyNoInteractions(facultyRepository, lecturerCodeGenerator, teachingSubjectRepository);
    }

    @Test
    void duplicateEmailReportsTheEmailField() {
        when(identityFacade.findUserIdsByEmail("a@example.test")).thenReturn(Set.of(90L));
        when(lecturerRepository.existsByUserIdInAndDeletedFalse(Set.of(90L))).thenReturn(true);
        assertThatThrownBy(() -> lecturerCreationService.createLecturer(authenticatedJwt, request))
                .isInstanceOfSatisfying(ProfileCreationException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_LECTURER_EMAIL);
                    assertThat(exception.getField()).isEqualTo("email");
                });
        verifyNoInteractions(facultyRepository, lecturerCodeGenerator);
    }

    @Test
    void studentOnlyEmailDoesNotBlockLecturerCreation() {
        prepareCreation(true, null);
        when(identityFacade.findUserIdsByEmail("a@example.test")).thenReturn(Set.of(90L));
        lecturerCreationService.createLecturer(authenticatedJwt, request);
        verify(identityFacade).createLecturerAccountWithoutNotification(any());
    }

    @Test
    void duplicateCitizenIdDoesNotCreateAccount() {
        when(lecturerRepository.existsByCitizenIdAndDeletedFalse("012345678901"))
                .thenReturn(true);
        assertThatThrownBy(() -> lecturerCreationService.createLecturer(authenticatedJwt, request))
                .isInstanceOfSatisfying(
                        ProfileCreationException.class, exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DUPLICATE_LECTURER_CITIZEN_ID));
        verifyNoInteractions(facultyRepository, lecturerCodeGenerator);
        verify(identityFacade, never()).createLecturerAccountWithoutNotification(any());
    }

    @Test
    void rejectsMissingOrInactiveFaculty() {
        assertThatThrownBy(() -> lecturerCreationService.createLecturer(authenticatedJwt, request))
                .isInstanceOfSatisfying(
                        ProfileCreationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FACULTY_NOT_ACTIVE));
        verifyNoInteractions(lecturerCodeGenerator);
    }

    @Test
    void rejectsBlankOrOversizedReason() {
        for (String reason : List.of("   ", "x".repeat(501))) {
            request.setReason(reason);
            assertThatThrownBy(() -> lecturerCreationService.createLecturer(authenticatedJwt, request))
                    .isInstanceOf(ProfileCreationException.class);
        }
        verifyNoInteractions(lecturerRepository, facultyRepository, lecturerCodeGenerator);
    }

    @Test
    void rejectsMissingOrInvalidTeachingSubjectIds() {
        for (List<Long> subjectIds : List.of(List.<Long>of(), List.of(-1L), Arrays.asList((Long) null))) {
            request.setTeachingSubjectIds(subjectIds);
            assertThatThrownBy(() -> lecturerCreationService.createLecturer(authenticatedJwt, request))
                    .isInstanceOfSatisfying(
                            ProfileCreationException.class,
                            exception -> assertThat(exception.getField()).isEqualTo("teachingSubjectIds"));
        }
        verifyNoInteractions(facultyRepository, lecturerCodeGenerator);
    }

    @Test
    void requiresPrincipalEvenWhenCalledOutsideController() {
        assertThatThrownBy(() -> lecturerCreationService.createLecturer(null, request))
                .isInstanceOfSatisfying(
                        ProfileCreationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        verifyNoInteractions(identityFacade, transactionManager);
    }

    private void prepareCreation(boolean emailSent, String emailError) {
        Faculty faculty = new Faculty();
        faculty.setId(1L);
        faculty.setFacultyCode("CNTT");
        when(facultyRepository.findActiveFacultyForCodeAllocation(1L)).thenReturn(Optional.of(faculty));
        when(lecturerCodeGenerator.allocateNextLecturerSequence(faculty, 2026)).thenReturn(1);
        when(lecturerCodeGenerator.generateLecturerCode(faculty, 2026, 1)).thenReturn("CNTT26001");
        when(identityFacade.createLecturerAccountWithoutNotification(any())).thenReturn(2L);
        when(lecturerRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            savedLecturer = invocation.getArgument(0);
            savedLecturer.setId(3L);
            return savedLecturer;
        });
        when(curriculumTeachingSubjects.requireActiveTeachingSubjects(Set.of(10L, 11L)))
                .thenReturn(List.of(
                        SubjectSimpleResponse.builder()
                                .id(10L)
                                .subjectCode("CS101")
                                .build(),
                        SubjectSimpleResponse.builder()
                                .id(11L)
                                .subjectCode("CS102")
                                .build()));
        when(identityFacade.sendLecturerAccountNotification(2L))
                .thenReturn(new AccountNotificationResponse(2L, emailSent, emailError));
    }
}
