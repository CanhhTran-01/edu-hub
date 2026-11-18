package com.eduhub.edu_hub.enrollment.api.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eduhub.edu_hub.enrollment.api.dto.request.UpdateProcessScoresRequest;
import com.eduhub.edu_hub.enrollment.api.exception.EnrollmentExceptionHandler;
import com.eduhub.edu_hub.enrollment.application.exception.EnrollmentException;
import com.eduhub.edu_hub.enrollment.application.service.ProcessScoreService;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.config.TimeConfig;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.exception.GlobalExceptionHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProcessScoreControllerTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-18T17:30:00Z"),
            new TimeConfig().applicationClock().getZone());
    private final ApiResponseFactory apiResponseFactory = new ApiResponseFactory(CLOCK);
    private final ProcessScoreService service = mock(ProcessScoreService.class);
    private final Jwt principal = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("lecturer")
            .claim("token_use", "ACCESS")
            .build();
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(principal));
        mvc = MockMvcBuilders.standaloneSetup(new ProcessScoreController(apiResponseFactory, service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(
                        new EnrollmentExceptionHandler(apiResponseFactory),
                        new GlobalExceptionHandler(apiResponseFactory))
                .build();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void preservesMissingNullAndZeroAndUsesTheAuthenticatedPrincipal() throws Exception {
        mvc.perform(
                        patch("/api/v1/module-classes/100/process-scores")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {"students":[{"enrollmentId":10,"componentScore1":null,"note":""},
                                     {"enrollmentId":11,"componentScore2":0}]}
                        """))
                .andExpect(status().isOk());
        var request = ArgumentCaptor.forClass(UpdateProcessScoresRequest.class);
        verify(service).updateScores(eq(principal), eq(100L), request.capture());
        var first = request.getValue().getStudents().getFirst();
        assertThat(first.hasComponentScore1()).isTrue();
        assertThat(first.getComponentScore1()).isNull();
        assertThat(first.hasComponentScore2()).isFalse();
        assertThat(first.hasNote()).isTrue();
        var second = request.getValue().getStudents().get(1);
        assertThat(second.hasComponentScore1()).isFalse();
        assertThat(second.hasComponentScore2()).isTrue();
        assertThat(second.getComponentScore2()).isZero();
        assertThat(second.hasNote()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "studentCode",
                "fullName",
                "processScore",
                "finalExamScore",
                "courseScore10",
                "componentScore1Present",
                "notePresent",
                "lecturerId",
                "unknown"
            })
    void rejectsReadOnlyAndPresenceFieldsEvenWhenNull(String field) throws Exception {
        mvc.perform(patch("/api/v1/module-classes/100/process-scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"students\":[{\"enrollmentId\":10,\"componentScore1\":7,\"" + field + "\":null}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "\"7.5\"", "[]", "{}"})
    void rejectsCoercionOfNonNumericScoreTokens(String score) throws Exception {
        mvc.perform(patch("/api/v1/module-classes/100/process-scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"students\":[{\"enrollmentId\":10,\"componentScore1\":" + score + "}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("students[0].componentScore1"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.5", "true", "\"10\""})
    void rejectsCoercionOfEnrollmentIds(String enrollmentId) throws Exception {
        mvc.perform(patch("/api/v1/module-classes/100/process-scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"students\":[{\"enrollmentId\":" + enrollmentId + ",\"componentScore1\":7}]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsUnexpectedFieldsAtBatchLevel() throws Exception {
        mvc.perform(patch("/api/v1/module-classes/100/process-scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"students\":[],\"lecturerId\":7}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "true", "[]", "{}"})
    void rejectsNonTextNotesInsteadOfCoercingThem(String note) throws Exception {
        mvc.perform(patch("/api/v1/module-classes/100/process-scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"students\":[{\"enrollmentId\":10,\"note\":" + note + "}]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void preservesExcelRowErrorsAndDoesNotUseTheAdmissionUploadError() throws Exception {
        when(service.importScores(eq(principal), eq(100L), any()))
                .thenThrow(new EnrollmentException(
                        ErrorCode.PROCESS_SCORE_IMPORT_INVALID,
                        List.of(new FieldErrorDetail("rows[8].studentCode", "MSSV không thuộc lớp"))));
        mvc.perform(multipart("/api/v1/module-classes/100/process-scores/import")
                        .file(new MockMultipartFile("file", "scores.xlsx", null, new byte[] {1})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4504))
                .andExpect(jsonPath("$.errors[0].field").value("rows[8].studentCode"));
    }

    @Test
    void missingMultipartFileUsesTheGradingContract() throws Exception {
        mvc.perform(multipart("/api/v1/module-classes/100/process-scores/import"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4504));
        verifyNoInteractions(service);
    }

    @Test
    void templateReturnsBytesAndAnAttachmentName() throws Exception {
        when(service.createTemplate(principal, 100L)).thenReturn(new byte[] {1, 2});
        mvc.perform(get("/api/v1/module-classes/100/process-scores/template"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                                "Content-Disposition", "attachment; filename=module-class-100-process-scores.xlsx"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().bytes(new byte[] {1, 2}));
    }
}
