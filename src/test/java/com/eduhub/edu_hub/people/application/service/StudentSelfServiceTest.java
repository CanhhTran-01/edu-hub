package com.eduhub.edu_hub.people.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.application.facade.dto.MajorSimpleResponse;
import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.curriculum.domain.model.Major;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.exception.IdentityAuthenticationException;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.request.UpdateMyProfileRequest;
import com.eduhub.edu_hub.people.api.mapper.StudentResponseMapper;
import com.eduhub.edu_hub.people.application.exception.StudentProfileException;
import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import com.eduhub.edu_hub.people.domain.model.Cohort;
import com.eduhub.edu_hub.people.domain.model.Student;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.Jwt;

class StudentSelfServiceTest {
    private final IdentityFacade identityFacade = mock(IdentityFacade.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final CurriculumFacade.ReferenceData curriculumReferenceData = mock(CurriculumFacade.ReferenceData.class);
    private final StudentSelfService studentSelfService = new StudentSelfService(
            identityFacade, studentRepository, curriculumReferenceData, new StudentResponseMapper());
    private final Jwt authenticatedJwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("CT080101")
            .claim("uid", 7L)
            .claim("sid", 3L)
            .build();
    private Student student;

    @BeforeEach
    void setUp() {
        when(identityFacade.requireCurrentUser(authenticatedJwt))
                .thenReturn(UserInfoResponse.builder()
                        .id(7L)
                        .username("CT080101")
                        .email("old@example.com")
                        .role("STUDENT")
                        .build());
        student = new Student();
        student.setId(9L);
        student.setUserId(7L);
        student.setStudentCode("CT080101");
        student.setFullName("Tên hồ sơ gốc");
        student.setPhone("0900000000");
        student.setTargetGpa(new BigDecimal("3.20"));
    }

    @Test
    void getUsesAuthenticatedUserIdAndMapsLoadedStudentWithoutLoadingByStudentIdAgain() {
        var major = Major.builder().build();
        major.setId(2L);
        var administrativeClass = new AdministrativeClass();
        administrativeClass.setMajor(major);
        administrativeClass.setClassName("CT8A");
        administrativeClass.setTrainingType(TrainingType.CHINH_QUY);
        var cohort = new Cohort();
        cohort.setCohortCode("CT8");
        administrativeClass.setCohort(cohort);
        student.setAdministrativeClass(administrativeClass);
        when(studentRepository.findByUserIdAndDeletedFalse(7L)).thenReturn(Optional.of(student));
        when(curriculumReferenceData.findMajorsByIds(Set.of(2L)))
                .thenReturn(List.of(new MajorSimpleResponse(2L, "CNTT", "Công nghệ thông tin", null)));

        var response = studentSelfService.getMyProfile(authenticatedJwt);

        assertThat(response.getId()).isEqualTo(9L);
        assertThat(response.getMajorName()).isEqualTo("Công nghệ thông tin");
        assertThat(response.getEmail()).isEqualTo("old@example.com");
        assertThat(response.getTargetGpa()).isEqualByComparingTo("3.20");
        verify(studentRepository, never()).findByIdAndDeletedFalse(any());
    }

    @Test
    void patchChangesOnlyProvidedFieldsAndReturnsUpdatedProfile() {
        when(studentRepository.findMyProfileForUpdate(7L)).thenReturn(Optional.of(student));
        var request = new UpdateMyProfileRequest();
        request.setTargetGpa(new BigDecimal("4"));

        var response = studentSelfService.updateMyProfile(authenticatedJwt, request);

        assertThat(response.getTargetGpa()).isEqualByComparingTo("4");
        assertThat(response.getPhone()).isEqualTo("0900000000");
        assertThat(response.getFullName()).isEqualTo("Tên hồ sơ gốc");
        verify(identityFacade, never()).updateAccountEmail(any(), any());
        verify(studentRepository).save(student);
        verify(studentRepository, never()).findByIdAndDeletedFalse(any());
    }

    @Test
    void updatesAccountEmailAndIncludesItInStudentResponse() {
        when(studentRepository.findMyProfileForUpdate(7L)).thenReturn(Optional.of(student));
        var request = new UpdateMyProfileRequest();
        request.setEmail("  Student@Example.COM  ");
        request.setPhone(" 0912345678 ");

        var response = studentSelfService.updateMyProfile(authenticatedJwt, request);

        assertThat(response.getEmail()).isEqualTo("student@example.com");
        assertThat(response.getPhone()).isEqualTo("0912345678");
        assertThat(response.getTargetGpa()).isEqualByComparingTo("3.20");
        verify(identityFacade).updateAccountEmail(7L, "student@example.com");
        verify(studentRepository).save(student);
    }

    @Test
    void emailUpdateFailureStopsOtherProfileChanges() {
        when(studentRepository.findMyProfileForUpdate(7L)).thenReturn(Optional.of(student));
        doThrow(new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED))
                .when(identityFacade)
                .updateAccountEmail(7L, "new@example.com");
        var request = new UpdateMyProfileRequest();
        request.setEmail("new@example.com");
        request.setPhone("0912345678");
        request.setTargetGpa(BigDecimal.ONE);

        assertThatThrownBy(() -> studentSelfService.updateMyProfile(authenticatedJwt, request))
                .isInstanceOf(IdentityAuthenticationException.class);
        assertThat(student.getPhone()).isEqualTo("0900000000");
        assertThat(student.getTargetGpa()).isEqualByComparingTo("3.20");
        verify(studentRepository, never()).save(any());
    }

    @Test
    void noOpDoesNotWriteAndGpaComparisonIgnoresScale() {
        when(studentRepository.findMyProfileForUpdate(7L)).thenReturn(Optional.of(student));
        var request = new UpdateMyProfileRequest();
        request.setTargetGpa(new BigDecimal("3.2"));
        request.setPhone(" 0900000000 ");
        studentSelfService.updateMyProfile(authenticatedJwt, request);
        studentSelfService.updateMyProfile(authenticatedJwt, new UpdateMyProfileRequest());
        verify(studentRepository, never()).save(any());
        verify(identityFacade, never()).updateAccountEmail(any(), any());
    }

    @Test
    void emailOnlyPatchUpdatesAccountWithoutSavingStudent() {
        when(studentRepository.findMyProfileForUpdate(7L)).thenReturn(Optional.of(student));
        var request = new UpdateMyProfileRequest();
        request.setEmail("new@example.com");
        var response = studentSelfService.updateMyProfile(authenticatedJwt, request);
        assertThat(response.getEmail()).isEqualTo("new@example.com");
        verify(identityFacade).updateAccountEmail(7L, "new@example.com");
        verify(studentRepository, never()).save(any());
    }

    @Test
    void clearingOnlyAccountEmailDoesNotWriteStudent() {
        when(studentRepository.findMyProfileForUpdate(7L)).thenReturn(Optional.of(student));
        var request = new UpdateMyProfileRequest();
        request.setEmail(null);
        var response = studentSelfService.updateMyProfile(authenticatedJwt, request);
        assertThat(response.getEmail()).isNull();
        verify(identityFacade).updateAccountEmail(7L, null);
        verify(studentRepository, never()).save(any());
    }

    @Test
    void explicitNullClearsOnlySpecifiedField() {
        when(studentRepository.findMyProfileForUpdate(7L)).thenReturn(Optional.of(student));
        var request = new UpdateMyProfileRequest();
        request.setPhone(null);
        var response = studentSelfService.updateMyProfile(authenticatedJwt, request);
        assertThat(response.getPhone()).isNull();
        assertThat(response.getTargetGpa()).isEqualByComparingTo("3.20");
        assertThat(response.getEmail()).isEqualTo("old@example.com");
    }

    @Test
    void explicitNullClearsAccountEmailAndCanUnsetGpa() {
        when(studentRepository.findMyProfileForUpdate(7L)).thenReturn(Optional.of(student));
        var request = new UpdateMyProfileRequest();
        request.setEmail(null);
        request.setTargetGpa(null);
        var response = studentSelfService.updateMyProfile(authenticatedJwt, request);
        assertThat(response.getEmail()).isNull();
        assertThat(response.getTargetGpa()).isNull();
        assertThat(response.getPhone()).isEqualTo("0900000000");
        verify(identityFacade).updateAccountEmail(7L, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "4.01", "3.125"})
    void invalidGpaDoesNotMutateProfileOrSynchronizeEmail(String targetGpa) {
        when(studentRepository.findMyProfileForUpdate(7L)).thenReturn(Optional.of(student));
        var request = new UpdateMyProfileRequest();
        request.setEmail("new@example.com");
        request.setTargetGpa(new BigDecimal(targetGpa));
        assertThatThrownBy(() -> studentSelfService.updateMyProfile(authenticatedJwt, request))
                .isInstanceOf(StudentProfileException.class);
        assertThat(student.getTargetGpa()).isEqualByComparingTo("3.20");
        verify(studentRepository, never()).save(any());
        verify(identityFacade, never()).updateAccountEmail(any(), any());
    }

    @Test
    void rejectsMissingPrincipalAtBothServiceEntrypoints() {
        assertThatThrownBy(() -> studentSelfService.getMyProfile(null))
                .isInstanceOfSatisfying(StudentProfileException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHENTICATED));
        assertThatThrownBy(() -> studentSelfService.updateMyProfile(null, new UpdateMyProfileRequest()))
                .isInstanceOf(StudentProfileException.class);
        verifyNoInteractions(studentRepository, curriculumReferenceData);
        verify(identityFacade, never()).requireCurrentUser(null);
    }

    @Test
    void identityFailurePreventsReadingOrUpdatingStudent() {
        when(identityFacade.requireCurrentUser(authenticatedJwt))
                .thenThrow(new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED));
        assertThatThrownBy(() -> studentSelfService.getMyProfile(authenticatedJwt))
                .isInstanceOf(IdentityAuthenticationException.class);
        assertThatThrownBy(() -> studentSelfService.updateMyProfile(authenticatedJwt, new UpdateMyProfileRequest()))
                .isInstanceOf(IdentityAuthenticationException.class);
        verifyNoInteractions(studentRepository, curriculumReferenceData);
    }

    @Test
    void missingStudentIsRejectedForGetAndPatch() {
        assertThatThrownBy(() -> studentSelfService.getMyProfile(authenticatedJwt))
                .isInstanceOfSatisfying(StudentProfileException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.STUDENT_NOT_FOUND));
        assertThatThrownBy(() -> studentSelfService.updateMyProfile(authenticatedJwt, new UpdateMyProfileRequest()))
                .isInstanceOf(StudentProfileException.class);
        verifyNoInteractions(curriculumReferenceData);
        verify(studentRepository, never()).save(any());
    }
}
