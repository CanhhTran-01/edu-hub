package com.eduhub.edu_hub.people.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eduhub.edu_hub.people.api.dto.request.CreateStudentRequest;
import com.eduhub.edu_hub.people.api.dto.request.UpdateStudentRequest;
import com.eduhub.edu_hub.people.api.dto.response.CreateStudentResponse;
import com.eduhub.edu_hub.people.api.dto.response.StudentResponse;
import com.eduhub.edu_hub.people.api.exception.StudentProfileExceptionHandler;
import com.eduhub.edu_hub.people.application.service.StudentApplicationService;
import com.eduhub.edu_hub.people.application.service.StudentProfileUpdateService;
import com.eduhub.edu_hub.shared.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminStudentControllerTest {
    private final StudentApplicationService service = mock(StudentApplicationService.class);
    private final StudentProfileUpdateService profileUpdateService = mock(StudentProfileUpdateService.class);
    private final Jwt authenticatedJwt = Jwt.withTokenValue("test-token")
            .header("alg", "HS256")
            .subject("academic-user")
            .claim("uid", 7L)
            .claim("sid", 3L)
            .build();
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(authenticatedJwt));
        mvc = MockMvcBuilders.standaloneSetup(new AdminStudentController(service, profileUpdateService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new StudentProfileExceptionHandler(), new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @CsvSource({
        "POST,/admission/preview",
        "PUT,/admission/11111111-1111-1111-1111-111111111111/assignment",
        "POST,/admission/11111111-1111-1111-1111-111111111111/confirm"
    })
    void oldAdmissionEndpointsAreNoLongerAvailable(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), "/api/v1/students" + path))
                .andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "studentCode",
                "administrativeClassId",
                "majorId",
                "cohortId",
                "trainingType",
                "userId",
                "activityStatus",
                "academicWarningStatus",
                "academicWarningReason",
                "gender",
                "entranceScore",
                "emailPresent"
            })
    void updateRejectsUnsupportedFieldsIncludingNullAndMixedEditableFields(String field) throws Exception {
        for (String fieldValue : List.of("null", "1")) {
            mvc.perform(put("/api/v1/students/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"0900000000\",\"" + field + "\":" + fieldValue + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(1001))
                    .andExpect(jsonPath("$.errors[0].field").value(field));
        }
        verifyNoInteractions(service, profileUpdateService);
    }

    @Test
    void updatePassesAuthenticatedPrincipalAndNormalizesEmailBeforeValidation() throws Exception {
        when(profileUpdateService.updateStudentProfile(eq(authenticatedJwt), eq(1L), any()))
                .thenReturn(StudentResponse.builder()
                        .id(1L)
                        .email("new@example.com")
                        .build());

        mvc.perform(put("/api/v1/students/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\" NEW@EXAMPLE.COM \",\"targetGpa\":3.75}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("new@example.com"));

        var requestCaptor = ArgumentCaptor.forClass(UpdateStudentRequest.class);
        verify(profileUpdateService).updateStudentProfile(eq(authenticatedJwt), eq(1L), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getEmail()).isEqualTo("new@example.com");
        assertThat(requestCaptor.getValue().getTargetGpa()).isEqualByComparingTo("3.75");
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"email\":null}", "{\"email\":\"   \"}"})
    void updatePreservesNullVersusBlankEmailSemantics(String requestBody) throws Exception {
        mvc.perform(put("/api/v1/students/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());
        var requestCaptor = ArgumentCaptor.forClass(UpdateStudentRequest.class);
        verify(profileUpdateService).updateStudentProfile(eq(authenticatedJwt), eq(1L), requestCaptor.capture());
        if (requestBody.contains("   ")) {
            assertThat(requestCaptor.getValue().getEmail()).isEmpty();
        } else {
            assertThat(requestCaptor.getValue().getEmail()).isNull();
        }
    }

    @ParameterizedTest
    @CsvSource({"fullName,256", "phone,256", "statusReason,501"})
    void updateRejectsOversizedFieldsWithFieldErrors(String field, int characterCount) throws Exception {
        mvc.perform(put("/api/v1/students/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"" + field + "\":\"" + "x".repeat(characterCount) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value(field));
        verifyNoInteractions(profileUpdateService);
    }

    @Test
    void updateRejectsInvalidEmailAndDateFormatBeforeCallingService() throws Exception {
        mvc.perform(put("/api/v1/students/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
        mvc.perform(put("/api/v1/students/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dateOfBirth\":\"31/02/2005\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("dateOfBirth"));
        verifyNoInteractions(profileUpdateService);
    }

    @Test
    void duplicateCitizenIdConstraintReturnsConflictWithoutExposingDatabaseDetails() throws Exception {
        var constraintException = mock(org.hibernate.exception.ConstraintViolationException.class);
        when(constraintException.getConstraintName()).thenReturn("uk_students_active_citizen_id");
        when(profileUpdateService.updateStudentProfile(eq(authenticatedJwt), eq(1L), any()))
                .thenThrow(new DataIntegrityViolationException("sensitive SQL context", constraintException));

        mvc.perform(put("/api/v1/students/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"citizenId\":\"012345678901\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4114))
                .andExpect(jsonPath("$.errors[0].field").value("citizenId"))
                .andExpect(jsonPath("$.message").value("CCCD sinh viên đã tồn tại"));
    }

    @Test
    void unrelatedIntegrityErrorIsNotMisreportedAsDuplicateCitizenId() throws Exception {
        when(profileUpdateService.updateStudentProfile(eq(authenticatedJwt), eq(1L), any()))
                .thenThrow(new DataIntegrityViolationException("sensitive SQL context"));

        mvc.perform(put("/api/v1/students/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0900000000\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(1000));
    }

    @Test
    void stillCreatesSingleStudent() throws Exception {
        StudentResponse student = StudentResponse.builder()
                .id(1L)
                .studentCode("CT080101")
                .fullName("Nguyen Van An")
                .build();
        when(service.create(any(CreateStudentRequest.class)))
                .thenReturn(new CreateStudentResponse(student, List.of(), "SENT", null));

        mvc.perform(
                        post("/api/v1/students")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                    "fullName": "Nguyen Van An",
                                    "citizenId": "012345678901",
                                    "email": "an@example.com",
                                    "majorId": 1,
                                    "cohortId": 2,
                                    "administrativeClassId": 3,
                                    "trainingType": "CHINH_QUY",
                                    "reason": "Bo sung sinh vien"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.student.studentCode").value("CT080101"));
        verify(service).create(argThat(value -> value.getAdministrativeClassId().equals(3L)));
    }

    @Test
    void noLongerAllowsManualAccountNotificationResend() throws Exception {
        mvc.perform(post("/api/v1/students/1/account-notification/resend")).andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "4.01", "3.125"})
    void administrativeUpdateRejectsInvalidTargetGpaWithFieldError(String targetGpa) throws Exception {
        mvc.perform(put("/api/v1/students/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetGpa\":" + targetGpa + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("targetGpa"));
        verifyNoInteractions(service, profileUpdateService);
    }
}
