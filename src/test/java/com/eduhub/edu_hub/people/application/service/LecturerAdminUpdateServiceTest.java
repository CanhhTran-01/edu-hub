package com.eduhub.edu_hub.people.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.api.dto.response.SubjectSimpleResponse;
import com.eduhub.edu_hub.curriculum.application.exception.TeachingSubjectException;
import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.identity.api.dto.response.UserAccountEmailResponse;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.exception.IdentityAuthenticationException;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.request.UpdateLecturerProfileRequest;
import com.eduhub.edu_hub.people.api.mapper.LecturerResponseMapper;
import com.eduhub.edu_hub.people.application.exception.LecturerProfileException;
import com.eduhub.edu_hub.people.domain.enums.LecturerStatus;
import com.eduhub.edu_hub.people.domain.model.Faculty;
import com.eduhub.edu_hub.people.domain.model.Lecturer;
import com.eduhub.edu_hub.people.domain.model.LecturerTeachingSubject;
import com.eduhub.edu_hub.people.domain.repository.LecturerRepository;
import com.eduhub.edu_hub.people.domain.repository.LecturerTeachingSubjectRepository;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.text.TextsNormalizer;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;

class LecturerAdminUpdateServiceTest {
    private final LecturerRepository lecturerRepository = mock(LecturerRepository.class);
    private final LecturerTeachingSubjectRepository teachingSubjectRepository =
            mock(LecturerTeachingSubjectRepository.class);
    private final IdentityFacade identityFacade = mock(IdentityFacade.class);
    private final CurriculumFacade.TeachingSubjects curriculumTeachingSubjects =
            mock(CurriculumFacade.TeachingSubjects.class);
    private final LecturerAdminUpdateService lecturerAdminUpdateService = new LecturerAdminUpdateService(
            lecturerRepository,
            teachingSubjectRepository,
            identityFacade,
            curriculumTeachingSubjects,
            new ProfileAccountEmailService(identityFacade),
            new LecturerResponseMapper());
    private final Jwt authenticatedJwt = Jwt.withTokenValue("test-token")
            .header("alg", "HS256")
            .subject("academic-admin")
            .build();
    private final AtomicReference<String> accountEmail = new AtomicReference<>("original@example.com");
    private Lecturer lecturer;
    private LecturerTeachingSubject retainedSubject;
    private LecturerTeachingSubject removedSubject;
    private LecturerTeachingSubject restoredSubject;
    private LecturerTeachingSubject unchangedDeletedSubject;

    @BeforeEach
    void setUp() {
        Faculty faculty = new Faculty();
        faculty.setId(8L);
        lecturer = Lecturer.builder()
                .lecturerCode("CNTT26001")
                .userId(101L)
                .faculty(faculty)
                .recruitmentYear(2026)
                .fullName("Nguyễn Văn An")
                .dateOfBirth(LocalDate.of(1980, 1, 1))
                .citizenId("012345678901")
                .phone("0900000000")
                .status(LecturerStatus.INACTIVE)
                .build();
        lecturer.setId(1L);
        retainedSubject = teachingLink(100L, 10L, false);
        removedSubject = teachingLink(200L, 20L, false);
        restoredSubject = teachingLink(300L, 30L, true);
        unchangedDeletedSubject = teachingLink(400L, 40L, true);
        when(identityFacade.requireCurrentUser(authenticatedJwt))
                .thenReturn(UserInfoResponse.builder().id(7L).build());
        when(lecturerRepository.findLecturerProfileByIdForUpdate(1L)).thenReturn(Optional.of(lecturer));
        when(teachingSubjectRepository.findByLecturerIdOrderBySubjectIdAsc(1L))
                .thenReturn(List.of(retainedSubject, removedSubject, restoredSubject, unchangedDeletedSubject));
        when(curriculumTeachingSubjects.findTeachingSubjectsByIds(anySet()))
                .thenAnswer(invocation -> subjects(invocation.getArgument(0)));
        when(curriculumTeachingSubjects.requireActiveTeachingSubjects(anySet()))
                .thenAnswer(invocation -> subjects(invocation.getArgument(0)));
        when(identityFacade.findAccountEmailsByUserIds(Set.of(101L)))
                .thenAnswer(invocation -> List.of(new UserAccountEmailResponse(101L, accountEmail.get())));
        doAnswer(invocation -> {
                    accountEmail.set(TextsNormalizer.normalizeEmail(invocation.getArgument(1)));
                    return null;
                })
                .when(identityFacade)
                .updateAccountEmail(eq(101L), any());
    }

    @Test
    void updatesManagedProfileEmailAndFinalSubjectSetWhilePreservingIdentity() {
        UpdateLecturerProfileRequest request = new UpdateLecturerProfileRequest();
        request.setFullName(" Trần Văn Bình ");
        request.setDateOfBirth(LocalDate.of(1979, 2, 3));
        request.setCitizenId("012 345 678 902");
        request.setPhone(" 0912345678 ");
        request.setEmail(" NEW@EXAMPLE.COM ");
        request.setTeachingSubjectIds(List.of(10L, 30L, 50L, 50L));
        request.setReason(" Cập nhật hồ sơ và môn giảng dạy ");

        var response = lecturerAdminUpdateService.updateLecturerProfile(authenticatedJwt, 1L, request);

        assertThat(response.getFullName()).isEqualTo("Trần Văn Bình");
        assertThat(response.getDateOfBirth()).isEqualTo(LocalDate.of(1979, 2, 3));
        assertThat(response.getCitizenId()).isEqualTo("012345678902");
        assertThat(response.getEmail()).isEqualTo("new@example.com");
        assertThat(response.getPhone()).isEqualTo("0912345678");
        assertThat(response.getLecturerCode()).isEqualTo("CNTT26001");
        assertThat(response.getFacultyId()).isEqualTo(8L);
        assertThat(response.getRecruitmentYear()).isEqualTo(2026);
        assertThat(response.getStatus()).isEqualTo(LecturerStatus.INACTIVE);
        assertThat(lecturer.getUserId()).isEqualTo(101L);
        assertThat(response.getTeachingSubjects())
                .extracting(SubjectSimpleResponse::getId)
                .containsExactly(10L, 30L, 50L);
        verify(lecturerRepository).save(same(lecturer));
        verify(identityFacade).updateAccountEmail(101L, "new@example.com");
        verify(curriculumTeachingSubjects).requireActiveTeachingSubjects(Set.of(10L, 30L, 50L));
        ArgumentCaptor<List<LecturerTeachingSubject>> changedLinks = ArgumentCaptor.forClass(List.class);
        verify(teachingSubjectRepository).saveAll(changedLinks.capture());
        assertThat(changedLinks.getValue())
                .extracting(LecturerTeachingSubject::getSubjectId)
                .containsExactly(20L, 30L, 50L);
        assertThat(changedLinks.getValue().get(1)).isSameAs(restoredSubject);
        assertThat(restoredSubject.getId()).isEqualTo(300L);
        assertThat(restoredSubject.isDeleted()).isFalse();
        assertThat(removedSubject.isDeleted()).isTrue();
        assertThat(retainedSubject.isDeleted()).isFalse();
        assertThat(unchangedDeletedSubject.isDeleted()).isTrue();
        assertThat(changedLinks.getValue().getLast().getLecturerId()).isEqualTo(1L);
        var callOrder = inOrder(lecturerRepository, curriculumTeachingSubjects, identityFacade);
        callOrder.verify(lecturerRepository).findLecturerProfileByIdForUpdate(1L);
        callOrder.verify(curriculumTeachingSubjects).requireActiveTeachingSubjects(Set.of(10L, 30L, 50L));
        callOrder.verify(identityFacade).updateAccountEmail(101L, "new@example.com");
        callOrder.verify(lecturerRepository).save(same(lecturer));
        verify(identityFacade, never()).createLecturerAccountWithoutNotification(any());
        verify(identityFacade, never()).sendLecturerAccountNotification(any());
    }

    @Test
    void omittedOrNullFieldsPreserveProfileAndHistoricalSubjectsWithoutRevalidatingSelection() {
        var response = lecturerAdminUpdateService.updateLecturerProfile(
                authenticatedJwt, 1L, new UpdateLecturerProfileRequest());
        assertThat(response.getFullName()).isEqualTo("Nguyễn Văn An");
        assertThat(response.getCitizenId()).isEqualTo("012345678901");
        assertThat(response.getPhone()).isEqualTo("0900000000");
        assertThat(response.getEmail()).isEqualTo("original@example.com");
        assertThat(response.getTeachingSubjects())
                .extracting(SubjectSimpleResponse::getId)
                .containsExactly(10L, 20L);
        verify(curriculumTeachingSubjects).findTeachingSubjectsByIds(Set.of(10L, 20L));
        verify(curriculumTeachingSubjects, never()).requireActiveTeachingSubjects(any());
        verify(identityFacade, never()).updateAccountEmail(any(), any());
        verify(teachingSubjectRepository, never()).saveAll(any());
    }

    @Test
    void repeatedCurrentSubjectIdsDoNotCreateOrRewriteLinks() {
        UpdateLecturerProfileRequest request = new UpdateLecturerProfileRequest();
        request.setTeachingSubjectIds(List.of(20L, 10L, 10L));
        var response = lecturerAdminUpdateService.updateLecturerProfile(authenticatedJwt, 1L, request);
        assertThat(response.getTeachingSubjects())
                .extracting(SubjectSimpleResponse::getId)
                .containsExactly(10L, 20L);
        verify(teachingSubjectRepository, never()).saveAll(any());
        verify(curriculumTeachingSubjects).requireActiveTeachingSubjects(Set.of(10L, 20L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankEmailClearsOnlyAccountEmail(String email) {
        UpdateLecturerProfileRequest request = new UpdateLecturerProfileRequest();
        request.setEmail(email);
        var response = lecturerAdminUpdateService.updateLecturerProfile(authenticatedJwt, 1L, request);
        assertThat(response.getEmail()).isNull();
        assertThat(response.getFullName()).isEqualTo("Nguyễn Văn An");
        verify(identityFacade).updateAccountEmail(101L, "");
        verify(identityFacade, never()).findUserIdsByEmail(any());
    }

    @Test
    void emailAndCitizenIdChecksExcludeTheLecturerBeingUpdated() {
        UpdateLecturerProfileRequest request = new UpdateLecturerProfileRequest();
        request.setEmail("original@example.com");
        request.setCitizenId("012345678901");
        when(identityFacade.findUserIdsByEmail("original@example.com")).thenReturn(Set.of(101L, 999L));
        lecturerAdminUpdateService.updateLecturerProfile(authenticatedJwt, 1L, request);
        verify(lecturerRepository).existsByUserIdInAndIdNotAndDeletedFalse(Set.of(101L, 999L), 1L);
        verify(lecturerRepository).existsByCitizenIdAndIdNotAndDeletedFalse("012345678901", 1L);
    }

    @Test
    void duplicateEmailRejectsTheWholeRequestBeforeAnyWrite() {
        UpdateLecturerProfileRequest request = changedNameAndEmail();
        when(identityFacade.findUserIdsByEmail("new@example.com")).thenReturn(Set.of(202L));
        when(lecturerRepository.existsByUserIdInAndIdNotAndDeletedFalse(Set.of(202L), 1L))
                .thenReturn(true);
        assertThatThrownBy(() -> lecturerAdminUpdateService.updateLecturerProfile(authenticatedJwt, 1L, request))
                .isInstanceOfSatisfying(LecturerProfileException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_LECTURER_EMAIL);
                    assertThat(exception.getField()).isEqualTo("email");
                });
        assertThat(lecturer.getFullName()).isEqualTo("Nguyễn Văn An");
        verify(identityFacade, never()).updateAccountEmail(any(), any());
        verify(lecturerRepository, never()).save(any());
        verifyNoInteractions(teachingSubjectRepository);
    }

    @Test
    void duplicateCitizenIdDoesNotChangeEmailOrProfile() {
        UpdateLecturerProfileRequest request = changedNameAndEmail();
        request.setCitizenId("012345678902");
        when(lecturerRepository.existsByCitizenIdAndIdNotAndDeletedFalse("012345678902", 1L))
                .thenReturn(true);
        assertThatThrownBy(() -> lecturerAdminUpdateService.updateLecturerProfile(authenticatedJwt, 1L, request))
                .isInstanceOfSatisfying(LecturerProfileException.class, exception -> assertThat(exception.getField())
                        .isEqualTo("citizenId"));
        assertThat(lecturer.getFullName()).isEqualTo("Nguyễn Văn An");
        verify(identityFacade, never()).updateAccountEmail(any(), any());
        verify(lecturerRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "123456", "01234567890A", "0123456789012"})
    void rejectsMalformedCitizenIdBeforeLockingOrWriting(String citizenId) {
        UpdateLecturerProfileRequest request = new UpdateLecturerProfileRequest();
        request.setCitizenId(citizenId);
        assertThatThrownBy(() -> lecturerAdminUpdateService.updateLecturerProfile(authenticatedJwt, 1L, request))
                .isInstanceOfSatisfying(LecturerProfileException.class, exception -> assertThat(exception.getField())
                        .isEqualTo("citizenId"));
        verifyNoInteractions(lecturerRepository, teachingSubjectRepository, curriculumTeachingSubjects);
    }

    @Test
    void rejectsInvalidPersonalFieldsBeforeWriting() {
        for (String fullName : List.of("  ", "x".repeat(256))) {
            UpdateLecturerProfileRequest request = new UpdateLecturerProfileRequest();
            request.setFullName(fullName);
            assertThatThrownBy(() -> lecturerAdminUpdateService.updateLecturerProfile(authenticatedJwt, 1L, request))
                    .isInstanceOf(LecturerProfileException.class);
        }
        for (LocalDate dateOfBirth : List.of(
                LocalDate.now(ZoneOffset.UTC), LocalDate.now(ZoneOffset.UTC).plusDays(1))) {
            UpdateLecturerProfileRequest request = new UpdateLecturerProfileRequest();
            request.setDateOfBirth(dateOfBirth);
            assertThatThrownBy(() -> lecturerAdminUpdateService.updateLecturerProfile(authenticatedJwt, 1L, request))
                    .isInstanceOf(LecturerProfileException.class);
        }
        UpdateLecturerProfileRequest oversizedPhone = new UpdateLecturerProfileRequest();
        oversizedPhone.setPhone("x".repeat(256));
        assertThatThrownBy(() -> lecturerAdminUpdateService.updateLecturerProfile(authenticatedJwt, 1L, oversizedPhone))
                .isInstanceOf(LecturerProfileException.class);
        UpdateLecturerProfileRequest oversizedReason = new UpdateLecturerProfileRequest();
        oversizedReason.setReason("x".repeat(501));
        assertThatThrownBy(
                        () -> lecturerAdminUpdateService.updateLecturerProfile(authenticatedJwt, 1L, oversizedReason))
                .isInstanceOf(LecturerProfileException.class);
        verifyNoInteractions(lecturerRepository, teachingSubjectRepository);
    }

    @Test
    void rejectsEmptyOrInvalidTeachingSubjectIdsBeforeAnyWrite() {
        for (List<Long> subjectIds : List.of(List.<Long>of(), List.of(0L), List.of(-1L), Arrays.asList((Long) null))) {
            UpdateLecturerProfileRequest request = new UpdateLecturerProfileRequest();
            request.setTeachingSubjectIds(subjectIds);
            assertThatThrownBy(() -> lecturerAdminUpdateService.updateLecturerProfile(authenticatedJwt, 1L, request))
                    .isInstanceOfSatisfying(
                            LecturerProfileException.class,
                            exception -> assertThat(exception.getField()).isEqualTo("teachingSubjectIds"));
        }
        verifyNoInteractions(lecturerRepository, teachingSubjectRepository, curriculumTeachingSubjects);
    }

    @ParameterizedTest
    @EnumSource(
            value = ErrorCode.class,
            names = {"SUBJECT_NOT_FOUND", "SUBJECT_NOT_TEACHABLE"})
    void unavailableSubjectsPreventEmailProfileAndLinkChanges(ErrorCode errorCode) {
        UpdateLecturerProfileRequest request = changedNameAndEmail();
        request.setTeachingSubjectIds(List.of(10L, 30L));
        when(curriculumTeachingSubjects.requireActiveTeachingSubjects(Set.of(10L, 30L)))
                .thenThrow(new TeachingSubjectException(errorCode));
        assertThatThrownBy(() -> lecturerAdminUpdateService.updateLecturerProfile(authenticatedJwt, 1L, request))
                .isInstanceOf(TeachingSubjectException.class);
        assertThat(lecturer.getFullName()).isEqualTo("Nguyễn Văn An");
        assertThat(restoredSubject.isDeleted()).isTrue();
        verify(identityFacade, never()).updateAccountEmail(any(), any());
        verify(lecturerRepository, never()).save(any());
        verify(teachingSubjectRepository, never()).saveAll(any());
    }

    @Test
    void emailWriteFailurePropagatesWithoutMutatingProfileOrLinks() {
        UpdateLecturerProfileRequest request = changedNameAndEmail();
        request.setTeachingSubjectIds(List.of(10L, 30L));
        doThrow(new DataIntegrityViolationException("Email conflict"))
                .when(identityFacade)
                .updateAccountEmail(101L, "new@example.com");
        assertThatThrownBy(() -> lecturerAdminUpdateService.updateLecturerProfile(authenticatedJwt, 1L, request))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(lecturer.getFullName()).isEqualTo("Nguyễn Văn An");
        assertThat(restoredSubject.isDeleted()).isTrue();
        verify(lecturerRepository, never()).save(any());
        verify(teachingSubjectRepository, never()).saveAll(any());
    }

    @Test
    void linkWriteFailureIsNotSwallowedAfterProfileAndEmailChanges() {
        UpdateLecturerProfileRequest request = changedNameAndEmail();
        request.setTeachingSubjectIds(List.of(30L));
        when(teachingSubjectRepository.saveAll(any()))
                .thenThrow(new DataIntegrityViolationException("Link write failure"));
        assertThatThrownBy(() -> lecturerAdminUpdateService.updateLecturerProfile(authenticatedJwt, 1L, request))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(identityFacade).updateAccountEmail(101L, "new@example.com");
        verify(lecturerRepository).save(same(lecturer));
    }

    @Test
    void unlinkedLegacyProfileCanUpdatePersonalFieldsWithoutCreatingAccount() {
        lecturer.setUserId(null);
        lecturer.setRecruitmentYear(null);
        lecturer.setLecturerCode("GV000001");
        UpdateLecturerProfileRequest request = new UpdateLecturerProfileRequest();
        request.setPhone(" 0912345678 ");
        var response = lecturerAdminUpdateService.updateLecturerProfile(authenticatedJwt, 1L, request);
        assertThat(response.getPhone()).isEqualTo("0912345678");
        assertThat(response.getEmail()).isNull();
        assertThat(response.getRecruitmentYear()).isNull();
        assertThat(response.getLecturerCode()).isEqualTo("GV000001");
        verify(identityFacade, never()).findAccountEmailsByUserIds(any());
        verify(identityFacade, never()).createLecturerAccountWithoutNotification(any());
    }

    @Test
    void changingEmailRequiresAnAccountLink() {
        lecturer.setUserId(null);
        assertThatThrownBy(() ->
                        lecturerAdminUpdateService.updateLecturerProfile(authenticatedJwt, 1L, changedNameAndEmail()))
                .isInstanceOfSatisfying(
                        LecturerProfileException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
        verify(lecturerRepository, never()).save(any());
        verifyNoInteractions(teachingSubjectRepository);
    }

    @Test
    void missingLecturerReturnsNotFound() {
        when(lecturerRepository.findLecturerProfileByIdForUpdate(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> lecturerAdminUpdateService.updateLecturerProfile(
                        authenticatedJwt, 1L, new UpdateLecturerProfileRequest()))
                .isInstanceOfSatisfying(
                        LecturerProfileException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LECTURER_NOT_FOUND));
        verifyNoInteractions(teachingSubjectRepository);
    }

    @Test
    void principalIsRequiredForDirectServiceCalls() {
        assertThatThrownBy(() ->
                        lecturerAdminUpdateService.updateLecturerProfile(null, 1L, new UpdateLecturerProfileRequest()))
                .isInstanceOfSatisfying(
                        LecturerProfileException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        verifyNoInteractions(identityFacade, lecturerRepository, teachingSubjectRepository);
    }

    @Test
    void invalidActorSessionPreventsAllProfileAccess() {
        when(identityFacade.requireCurrentUser(authenticatedJwt))
                .thenThrow(new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED));
        assertThatThrownBy(() -> lecturerAdminUpdateService.updateLecturerProfile(
                        authenticatedJwt, 1L, new UpdateLecturerProfileRequest()))
                .isInstanceOf(IdentityAuthenticationException.class);
        verifyNoInteractions(lecturerRepository, teachingSubjectRepository, curriculumTeachingSubjects);
    }

    private UpdateLecturerProfileRequest changedNameAndEmail() {
        UpdateLecturerProfileRequest request = new UpdateLecturerProfileRequest();
        request.setFullName("Trần Văn Bình");
        request.setEmail("new@example.com");
        return request;
    }

    private LecturerTeachingSubject teachingLink(Long teachingLinkId, Long subjectId, boolean isDeleted) {
        LecturerTeachingSubject teachingLink = LecturerTeachingSubject.builder()
                .lecturerId(1L)
                .subjectId(subjectId)
                .build();
        teachingLink.setId(teachingLinkId);
        teachingLink.setDeleted(isDeleted);
        return teachingLink;
    }

    private List<SubjectSimpleResponse> subjects(Set<Long> subjectIds) {
        return subjectIds.stream()
                .sorted()
                .map(subjectId -> SubjectSimpleResponse.builder()
                        .id(subjectId)
                        .subjectCode("CS" + subjectId)
                        .subjectName("Môn " + subjectId)
                        .credits(3)
                        .build())
                .toList();
    }
}
