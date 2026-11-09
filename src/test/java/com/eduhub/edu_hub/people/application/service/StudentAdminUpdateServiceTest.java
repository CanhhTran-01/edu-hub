package com.eduhub.edu_hub.people.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.curriculum.application.facade.CurriculumFacade;
import com.eduhub.edu_hub.curriculum.application.facade.dto.MajorSimpleResponse;
import com.eduhub.edu_hub.curriculum.domain.enums.TrainingType;
import com.eduhub.edu_hub.curriculum.domain.model.Major;
import com.eduhub.edu_hub.identity.api.dto.response.UserAccountEmailResponse;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.exception.IdentityAuthenticationException;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.people.api.dto.request.UpdateStudentProfileRequest;
import com.eduhub.edu_hub.people.api.mapper.StudentResponseMapper;
import com.eduhub.edu_hub.people.application.exception.StudentProfileException;
import com.eduhub.edu_hub.people.domain.model.AdministrativeClass;
import com.eduhub.edu_hub.people.domain.model.Cohort;
import com.eduhub.edu_hub.people.domain.model.Student;
import com.eduhub.edu_hub.people.domain.repository.StudentRepository;
import com.eduhub.edu_hub.shared.enums.ActivityStatus;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class StudentAdminUpdateServiceTest {
    private final Jwt authenticatedJwt = Jwt.withTokenValue("test-token")
            .header("alg", "HS256")
            .subject("academic-user")
            .claim("uid", 7L)
            .claim("sid", 3L)
            .build();

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private IdentityFacade identityFacade;

    @Mock
    private CurriculumFacade.ReferenceData curriculumReferenceData;

    @Spy
    private StudentResponseMapper studentResponseMapper = new StudentResponseMapper();

    @InjectMocks
    private StudentAdminUpdateService service;

    @Test
    void normalizesEditableFieldsAndPreservesStudentIdentityClassAndAcademicWarning() {
        Student student = loadStudent();
        AdministrativeClass administrativeClass = new AdministrativeClass();
        administrativeClass.setId(30L);
        administrativeClass.setClassName("CT8A");
        administrativeClass.setMajor(Major.builder().build());
        administrativeClass.getMajor().setId(2L);
        administrativeClass.setCohort(new Cohort());
        administrativeClass.getCohort().setCohortCode("CT8");
        administrativeClass.setTrainingType(TrainingType.CHINH_QUY);
        student.setAdministrativeClass(administrativeClass);
        prepareProfileResponse(student, "new@example.com");
        when(curriculumReferenceData.findMajorsByIds(Set.of(2L)))
                .thenReturn(List.of(new MajorSimpleResponse(2L, "CNTT", "Công nghệ thông tin", null)));
        UpdateStudentProfileRequest request = new UpdateStudentProfileRequest();
        request.setFullName(" Nguyễn Văn Bình ");
        request.setDateOfBirth(LocalDate.of(2005, 2, 3));
        request.setPhone(" 0911111111 ");
        request.setCitizenId("012 345 678 901");
        request.setEmail(" NEW@EXAMPLE.COM ");
        request.setTargetGpa(new BigDecimal("3.75"));
        request.setStatusReason(" Đính chính hồ sơ ");

        var response = service.updateStudentProfile(authenticatedJwt, 1L, request);

        assertThat(response.getFullName()).isEqualTo("Nguyễn Văn Bình");
        assertThat(response.getDateOfBirth()).isEqualTo(LocalDate.of(2005, 2, 3));
        assertThat(response.getPhone()).isEqualTo("0911111111");
        assertThat(response.getCitizenId()).isEqualTo("012345678901");
        assertThat(response.getEmail()).isEqualTo("new@example.com");
        assertThat(response.getTargetGpa()).isEqualByComparingTo("3.75");
        assertThat(response.getMajorName()).isEqualTo("Công nghệ thông tin");
        assertThat(response.getAcademicWarningStatus()).isEqualTo("LEVEL_1");
        assertThat(response.getAcademicWarningReason()).isEqualTo("GPA thực tế dưới ngưỡng");
        assertThat(student.getAcademicWarningStatus()).isEqualTo("LEVEL_1");
        assertThat(student.getAcademicWarningReason()).isEqualTo("GPA thực tế dưới ngưỡng");
        assertThat(student.getAdministrativeClass()).isSameAs(administrativeClass);
        assertThat(student.getStudentCode()).isEqualTo("CNTT080101");
        assertThat(student.getUserId()).isEqualTo(101L);
        assertThat(student.getActivityStatus()).isEqualTo(ActivityStatus.STUDYING);
        verify(studentRepository).existsByCitizenIdAndIdNotAndDeletedFalse("012345678901", 1L);
        verify(identityFacade).updateAccountEmail(101L, "new@example.com");
        verify(studentRepository).save(same(student));
    }

    @Test
    void nullAndOmittedFieldsKeepExistingValuesAndDoNotWriteAccountEmail() {
        Student student = loadStudent();
        prepareProfileResponse(student, "kept@example.com");
        UpdateStudentProfileRequest request = new UpdateStudentProfileRequest();
        request.setFullName(null);
        request.setDateOfBirth(null);
        request.setEmail(null);
        request.setPhone(null);
        request.setCitizenId(null);
        request.setTargetGpa(null);

        var response = service.updateStudentProfile(authenticatedJwt, 1L, request);

        assertThat(response.getFullName()).isEqualTo("Tên gốc");
        assertThat(response.getDateOfBirth()).isEqualTo(LocalDate.of(2006, 1, 2));
        assertThat(response.getPhone()).isEqualTo("0900000000");
        assertThat(response.getCitizenId()).isEqualTo("012345678901");
        assertThat(response.getTargetGpa()).isEqualByComparingTo("2.50");
        assertThat(response.getEmail()).isEqualTo("kept@example.com");
        verify(identityFacade, never()).updateAccountEmail(any(), any());
        verify(studentRepository, never()).existsByCitizenIdAndIdNotAndDeletedFalse(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    void blankEmailRequestsClearingAtIdentityInsteadOfKeepingTheOldEmail(String blankEmail) {
        Student student = loadStudent();
        prepareProfileResponse(student, null);
        UpdateStudentProfileRequest request = new UpdateStudentProfileRequest();
        request.setEmail(blankEmail);

        assertThat(service.updateStudentProfile(authenticatedJwt, 1L, request).getEmail())
                .isNull();
        verify(identityFacade).updateAccountEmail(101L, "");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "4.01", "3.125"})
    void rejectsInvalidTargetGpaBeforeLoadingOrChangingTheProfile(String targetGpa) {
        authenticateCurrentUser();
        UpdateStudentProfileRequest request = new UpdateStudentProfileRequest();
        request.setTargetGpa(new BigDecimal(targetGpa));
        request.setFullName("Không được ghi khi GPA sai");

        assertThatThrownBy(() -> service.updateStudentProfile(authenticatedJwt, 1L, request))
                .isInstanceOfSatisfying(StudentProfileException.class, exception -> assertThat(exception.getField())
                        .isEqualTo("targetGpa"));
        verifyNoInteractions(studentRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "123", "01234567890A", "0123456789012"})
    void rejectsInvalidCitizenIdBeforeAnyWrite(String citizenId) {
        authenticateCurrentUser();
        UpdateStudentProfileRequest request = new UpdateStudentProfileRequest();
        request.setCitizenId(citizenId);

        assertThatThrownBy(() -> service.updateStudentProfile(authenticatedJwt, 1L, request))
                .isInstanceOfSatisfying(StudentProfileException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getField()).isEqualTo("citizenId");
                });
        verifyNoInteractions(studentRepository);
        verify(identityFacade, never()).updateAccountEmail(any(), any());
    }

    @Test
    void duplicateCitizenIdDoesNotChangeEmailOrStudentFields() {
        Student student = loadStudent();
        UpdateStudentProfileRequest request = new UpdateStudentProfileRequest();
        request.setCitizenId("111 111 111 111");
        request.setFullName("Tên mới");
        request.setEmail("new@example.com");
        when(studentRepository.existsByCitizenIdAndIdNotAndDeletedFalse("111111111111", 1L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.updateStudentProfile(authenticatedJwt, 1L, request))
                .isInstanceOfSatisfying(StudentProfileException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_STUDENT_CITIZEN_ID);
                    assertThat(exception.getField()).isEqualTo("citizenId");
                });
        assertThat(student.getFullName()).isEqualTo("Tên gốc");
        assertThat(student.getCitizenId()).isEqualTo("012345678901");
        verify(studentRepository, never()).save(any());
        verify(identityFacade, never()).updateAccountEmail(any(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void rejectsTodayAndFutureBirthDates(int daysFromToday) {
        authenticateCurrentUser();
        UpdateStudentProfileRequest request = new UpdateStudentProfileRequest();
        request.setDateOfBirth(LocalDate.now(ZoneOffset.UTC).plusDays(daysFromToday));

        assertThatThrownBy(() -> service.updateStudentProfile(authenticatedJwt, 1L, request))
                .isInstanceOfSatisfying(StudentProfileException.class, exception -> assertThat(exception.getField())
                        .isEqualTo("dateOfBirth"));
        verifyNoInteractions(studentRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "\t"})
    void rejectsBlankFullNameWithAFieldError(String fullName) {
        authenticateCurrentUser();
        UpdateStudentProfileRequest request = new UpdateStudentProfileRequest();
        request.setFullName(fullName);

        assertThatThrownBy(() -> service.updateStudentProfile(authenticatedJwt, 1L, request))
                .isInstanceOfSatisfying(StudentProfileException.class, exception -> assertThat(exception.getField())
                        .isEqualTo("fullName"));
        verifyNoInteractions(studentRepository);
    }

    @ParameterizedTest
    @CsvSource({"fullName,256", "phone,256", "statusReason,501"})
    void rejectsOversizedFieldsBeforeLoadingTheStudent(String field, int characterCount) {
        authenticateCurrentUser();
        UpdateStudentProfileRequest request = new UpdateStudentProfileRequest();
        switch (field) {
            case "fullName" -> request.setFullName("x".repeat(characterCount));
            case "phone" -> request.setPhone("1".repeat(characterCount));
            case "statusReason" -> request.setStatusReason("x".repeat(characterCount));
        }

        assertThatThrownBy(() -> service.updateStudentProfile(authenticatedJwt, 1L, request))
                .isInstanceOfSatisfying(StudentProfileException.class, exception -> assertThat(exception.getField())
                        .isEqualTo(field));
        verifyNoInteractions(studentRepository);
    }

    @Test
    void missingOrDeletedStudentReturnsNotFound() {
        authenticateCurrentUser();

        assertThatThrownBy(() -> service.updateStudentProfile(authenticatedJwt, 1L, new UpdateStudentProfileRequest()))
                .isInstanceOfSatisfying(StudentProfileException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.STUDENT_NOT_FOUND));
        verify(studentRepository, never()).save(any());
    }

    @Test
    void emailUpdateRequiresAnAccountLinkAndDoesNotCreateAnAccount() {
        Student student = loadStudent();
        student.setUserId(null);
        UpdateStudentProfileRequest request = new UpdateStudentProfileRequest();
        request.setEmail("new@example.com");

        assertThatThrownBy(() -> service.updateStudentProfile(authenticatedJwt, 1L, request))
                .isInstanceOfSatisfying(StudentProfileException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
        verify(identityFacade, never()).updateAccountEmail(any(), any());
        verify(identityFacade, never()).createStudentAccountWithoutNotification(any());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void failedAccountEmailUpdateDoesNotMutateStudentFields() {
        Student student = loadStudent();
        doThrow(new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED))
                .when(identityFacade)
                .updateAccountEmail(101L, "new@example.com");
        UpdateStudentProfileRequest request = new UpdateStudentProfileRequest();
        request.setEmail("new@example.com");
        request.setFullName("Tên mới");

        assertThatThrownBy(() -> service.updateStudentProfile(authenticatedJwt, 1L, request))
                .isInstanceOf(IdentityAuthenticationException.class);
        assertThat(student.getFullName()).isEqualTo("Tên gốc");
        verify(studentRepository, never()).save(any());
    }

    @Test
    void updatesAnUnlinkedProfileWhenNoEmailChangeIsRequested() {
        Student student = loadStudent();
        student.setUserId(null);
        when(studentRepository.save(student)).thenReturn(student);
        UpdateStudentProfileRequest request = new UpdateStudentProfileRequest();
        request.setPhone(" 0911111111 ");

        var response = service.updateStudentProfile(authenticatedJwt, 1L, request);

        assertThat(response.getPhone()).isEqualTo("0911111111");
        assertThat(response.getEmail()).isNull();
        verify(identityFacade, never()).findAccountEmailsByUserIds(any());
    }

    @Test
    void rejectsMissingPrincipalEvenWhenCalledOutsideTheSecurityChain() {
        assertThatThrownBy(() -> service.updateStudentProfile(null, 1L, new UpdateStudentProfileRequest()))
                .isInstanceOfSatisfying(StudentProfileException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHENTICATED));
        verifyNoInteractions(studentRepository, identityFacade);
    }

    @Test
    void invalidCurrentUserOrSessionPreventsLoadingTheTargetProfile() {
        when(identityFacade.requireCurrentUser(authenticatedJwt))
                .thenThrow(new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED));

        assertThatThrownBy(() -> service.updateStudentProfile(authenticatedJwt, 1L, new UpdateStudentProfileRequest()))
                .isInstanceOf(IdentityAuthenticationException.class);
        verifyNoInteractions(studentRepository);
    }

    private void authenticateCurrentUser() {
        when(identityFacade.requireCurrentUser(authenticatedJwt))
                .thenReturn(UserInfoResponse.builder().id(7L).build());
    }

    private Student loadStudent() {
        authenticateCurrentUser();
        Student student = new Student();
        student.setId(1L);
        student.setUserId(101L);
        student.setStudentCode("CNTT080101");
        student.setFullName("Tên gốc");
        student.setDateOfBirth(LocalDate.of(2006, 1, 2));
        student.setPhone("0900000000");
        student.setCitizenId("012345678901");
        student.setTargetGpa(new BigDecimal("2.50"));
        student.setAcademicWarningStatus("LEVEL_1");
        student.setAcademicWarningReason("GPA thực tế dưới ngưỡng");
        student.setActivityStatus(ActivityStatus.STUDYING);
        when(studentRepository.findStudentProfileByIdForUpdate(1L)).thenReturn(Optional.of(student));
        return student;
    }

    private void prepareProfileResponse(Student student, String accountEmail) {
        when(studentRepository.save(student)).thenReturn(student);
        when(identityFacade.findAccountEmailsByUserIds(Set.of(101L)))
                .thenReturn(List.of(new UserAccountEmailResponse(101L, accountEmail)));
    }
}
