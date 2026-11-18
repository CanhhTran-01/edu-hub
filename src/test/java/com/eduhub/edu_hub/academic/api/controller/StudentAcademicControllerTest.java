package com.eduhub.edu_hub.academic.api.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eduhub.edu_hub.academic.api.dto.response.*;
import com.eduhub.edu_hub.academic.api.dto.response.StudentTranscriptResponse.*;
import com.eduhub.edu_hub.academic.api.exception.StudentAcademicExceptionHandler;
import com.eduhub.edu_hub.academic.application.exception.StudentAcademicException;
import com.eduhub.edu_hub.academic.application.service.StudentAcademicQueryService;
import com.eduhub.edu_hub.scheduling.api.dto.response.SemesterResponse;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import com.eduhub.edu_hub.shared.config.TimeConfig;
import com.eduhub.edu_hub.shared.exception.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StudentAcademicControllerTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-18T17:30:00Z"),
            new TimeConfig().applicationClock().getZone());
    private final ApiResponseFactory apiResponseFactory = new ApiResponseFactory(CLOCK);
    StudentAcademicQueryService service = mock(StudentAcademicQueryService.class);
    Jwt principal = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("student")
            .claim("token_use", "ACCESS")
            .build();
    MockMvc mvc;

    @BeforeEach
    void setup() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(principal));
        mvc = MockMvcBuilders.standaloneSetup(new StudentAcademicController(apiResponseFactory, service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(
                        new StudentAcademicExceptionHandler(apiResponseFactory),
                        new GlobalExceptionHandler(apiResponseFactory))
                .build();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void servesGroupedTranscriptWithoutChangingAuthenticatedStudentFromQuery() throws Exception {
        when(service.findMyTranscript(principal, 3L))
                .thenReturn(new StudentTranscriptResponse(
                        true,
                        "Phạm vi",
                        List.of(new SemesterTranscriptResponse(
                                SemesterResponse.builder().id(3L).build(),
                                BigDecimal.ZERO,
                                null,
                                0,
                                3,
                                1,
                                List.of()))));
        mvc.perform(get("/api/v1/students/me/academic/transcript")
                        .param("semesterId", "3")
                        .param("studentId", "999"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.semesters[0].semester.id").value(3))
                .andExpect(jsonPath("$.data.semesters[0].semesterGpa").value(0))
                .andExpect(jsonPath("$.data.semesters[0].cumulativeGpa").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.semesters[0].courses").isEmpty());
        verify(service).findMyTranscript(principal, 3L);
    }

    @Test
    void bindsAllTermsAndRegistrationRoutesAndRejectsMalformedFilter() throws Exception {
        mvc.perform(get("/api/v1/students/me/academic/transcript")).andExpect(status().isOk());
        verify(service).findMyTranscript(principal, null);
        mvc.perform(get("/api/v1/students/me/academic/registration").param("semesterId", "3"))
                .andExpect(status().isOk());
        verify(service).findMySemsterRegistrations(principal, 3L);
        mvc.perform(get("/api/v1/students/me/academic/registration")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/students/me/academic/transcript").param("semesterId", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void overviewUsesPrincipalAndMissingPrincipalReturnsBusiness401() throws Exception {
        mvc.perform(get("/api/v1/students/me/academic")).andExpect(status().isOk());
        verify(service).findMyAcademicOverview(principal);
        SecurityContextHolder.clearContext();
        when(service.findMyAcademicOverview(null)).thenThrow(new StudentAcademicException(ErrorCode.UNAUTHENTICATED));
        mvc.perform(get("/api/v1/students/me/academic").header("Authorization", "Bearer token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(4000));
    }
}
