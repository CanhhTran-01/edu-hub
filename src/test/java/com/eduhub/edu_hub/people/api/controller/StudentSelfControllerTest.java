package com.eduhub.edu_hub.people.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eduhub.edu_hub.people.api.dto.request.UpdateMyProfileRequest;
import com.eduhub.edu_hub.people.api.dto.response.StudentResponse;
import com.eduhub.edu_hub.people.api.exception.StudentProfileExceptionHandler;
import com.eduhub.edu_hub.people.application.exception.StudentProfileException;
import com.eduhub.edu_hub.people.application.service.StudentAdminUpdateService;
import com.eduhub.edu_hub.people.application.service.StudentApplicationService;
import com.eduhub.edu_hub.people.application.service.StudentSelfService;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StudentSelfControllerTest {
    StudentSelfService service = mock(StudentSelfService.class);
    StudentApplicationService studentApplicationService = mock(StudentApplicationService.class);
    StudentAdminUpdateService studentAdminUpdateService = mock(StudentAdminUpdateService.class);
    MockMvc mvc;
    Jwt authenticatedJwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("student")
            .claim("uid", 7L)
            .claim("sid", 3L)
            .build();

    @BeforeEach
    void setup() {
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(authenticatedJwt));
        mvc = MockMvcBuilders.standaloneSetup(
                        new StudentSelfController(service),
                        new AdminStudentController(studentApplicationService, studentAdminUpdateService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new StudentProfileExceptionHandler(), new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getAndPatchResolveLiteralMeRoute() throws Exception {
        when(service.getMyProfile(authenticatedJwt))
                .thenReturn(StudentResponse.builder().id(9L).build());
        mvc.perform(get("/api/v1/students/me").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9));
        verify(service).getMyProfile(authenticatedJwt);
        mvc.perform(patch("/api/v1/students/me")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0900000000\"}"))
                .andExpect(status().isOk());
        verify(service).updateMyProfile(eq(authenticatedJwt), any());
        verifyNoInteractions(studentApplicationService, studentAdminUpdateService);
    }

    @Test
    void profileByStudentIdUsesTheAdministrativeQueryInsteadOfTheSelfService() throws Exception {
        when(studentApplicationService.findById(12L))
                .thenReturn(StudentResponse.builder().id(12L).build());

        mvc.perform(get("/api/v1/students/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(12));

        verify(studentApplicationService).findById(12L);
        verifyNoInteractions(service, studentAdminUpdateService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "4.01", "3.125", "3.200"})
    void rejectsGpaOutOfRange(String gpa) throws Exception {
        mvc.perform(patch("/api/v1/students/me")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetGpa\":" + gpa + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("targetGpa"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "4", "3.25", "null"})
    void acceptsGpaBoundaries(String gpa) throws Exception {
        mvc.perform(patch("/api/v1/students/me")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetGpa\":" + gpa + "}"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "studentCode",
                "fullName",
                "userId",
                "studentId",
                "administrativeClassId",
                "majorId",
                "cohortId",
                "trainingType",
                "activityStatus",
                "dateOfBirth",
                "address",
                "createdBy",
                "actualGpa",
                "targetGpaPresent",
                "phonePresent",
                "emailPresent"
            })
    void rejectsLockedAndUnsupportedFields(String field) throws Exception {
        mvc.perform(patch("/api/v1/students/me")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"" + field + "\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.errors[0].field").value(field));
        verifyNoInteractions(service);
    }

    @Test
    void missingPrincipalCannotBeReplacedByAnUnverifiedAuthorizationHeader() throws Exception {
        SecurityContextHolder.clearContext();
        when(service.getMyProfile(null)).thenThrow(new StudentProfileException(ErrorCode.UNAUTHENTICATED));
        mvc.perform(get("/api/v1/students/me").header("Authorization", "Bearer token"))
                .andExpect(status().isUnauthorized());
        verify(service).getMyProfile(null);
    }

    @Test
    void acceptsEmailNormalizesItAndReturnsUpdatedResponse() throws Exception {
        when(service.updateMyProfile(eq(authenticatedJwt), any()))
                .thenReturn(
                        StudentResponse.builder().email("student@example.com").build());
        mvc.perform(patch("/api/v1/students/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\" Student@Example.COM \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("student@example.com"));
        var requestCaptor = ArgumentCaptor.forClass(UpdateMyProfileRequest.class);
        verify(service).updateMyProfile(eq(authenticatedJwt), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getEmail()).isEqualTo("student@example.com");
        assertThat(requestCaptor.getValue().hasEmail()).isTrue();
        assertThat(requestCaptor.getValue().hasPhone()).isFalse();
        assertThat(requestCaptor.getValue().hasTargetGpa()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "a b@example.com"})
    void rejectsInvalidEmailWithFieldError(String email) throws Exception {
        mvc.perform(patch("/api/v1/students/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsEmailLongerThanColumnLimit() throws Exception {
        String email = "a".repeat(64) + "@" + ("b".repeat(63) + ".").repeat(3) + "com";
        mvc.perform(patch("/api/v1/students/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
        verifyNoInteractions(service);
    }

    @Test
    void jsonPreservesMissingFieldsAndExplicitNulls() throws Exception {
        mvc.perform(patch("/api/v1/students/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/students/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":null,\"phone\":null,\"targetGpa\":null}"))
                .andExpect(status().isOk());
        var requestCaptor = ArgumentCaptor.forClass(UpdateMyProfileRequest.class);
        verify(service, times(2)).updateMyProfile(eq(authenticatedJwt), requestCaptor.capture());
        var emptyRequest = requestCaptor.getAllValues().getFirst();
        assertThat(emptyRequest.hasEmail()).isFalse();
        assertThat(emptyRequest.hasPhone()).isFalse();
        assertThat(emptyRequest.hasTargetGpa()).isFalse();
        var explicitNullRequest = requestCaptor.getAllValues().getLast();
        assertThat(explicitNullRequest.hasEmail()).isTrue();
        assertThat(explicitNullRequest.hasPhone()).isTrue();
        assertThat(explicitNullRequest.hasTargetGpa()).isTrue();
        assertThat(explicitNullRequest.getEmail()).isNull();
        assertThat(explicitNullRequest.getPhone()).isNull();
        assertThat(explicitNullRequest.getTargetGpa()).isNull();
    }

    @Test
    void rejectsEntireRequestWhenItMixesEditableAndLockedFields() throws Exception {
        mvc.perform(patch("/api/v1/students/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0912345678\",\"email\":\"new@example.com\",\"fullName\":\"Tên khác\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("fullName"));
        verifyNoInteractions(service);
    }

    @Test
    void invalidGpaJsonTypeHasSpecificFieldError() throws Exception {
        mvc.perform(patch("/api/v1/students/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetGpa\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("targetGpa"));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsPhoneLongerThanColumnLimit() throws Exception {
        mvc.perform(patch("/api/v1/students/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + "1".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("phone"));
        verifyNoInteractions(service);
    }
}
