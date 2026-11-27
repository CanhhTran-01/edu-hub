package com.eduhub.edu_hub.aiassistant.api.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eduhub.edu_hub.academic.api.dto.request.GpaSimulationRequest;
import com.eduhub.edu_hub.academic.api.dto.response.GpaSimulationPreviewResponse;
import com.eduhub.edu_hub.academic.api.dto.response.GpaSimulationSnapshotResponse;
import com.eduhub.edu_hub.academic.application.exception.GpaSimulationException;
import com.eduhub.edu_hub.aiassistant.api.dto.response.GpaSimulationCommentResponse;
import com.eduhub.edu_hub.aiassistant.api.exception.AiAssistantExceptionHandler;
import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.aiassistant.application.service.AiGpaSimulationService;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import com.eduhub.edu_hub.shared.exception.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
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

class AiGpaSimulationControllerTest {
    AiGpaSimulationService service = mock(AiGpaSimulationService.class);
    Jwt principal = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("student")
            .build();
    MockMvc mvc;

    @BeforeEach
    void setup() {
        var responses = new ApiResponseFactory(
                Clock.fixed(Instant.parse("2026-11-27T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(principal));
        mvc = MockMvcBuilders.standaloneSetup(new AiGpaSimulationController(service, responses))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new AiAssistantExceptionHandler(responses), new GlobalExceptionHandler(responses))
                .build();
    }

    @AfterEach
    void clearPrincipal() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void snapshotReturnsJsonWithoutBrowserCaching() throws Exception {
        when(service.findMySnapshot(principal))
                .thenReturn(GpaSimulationSnapshotResponse.builder()
                        .snapshotRef("a".repeat(64))
                        .semesterTargetGpa(new BigDecimal("3.5"))
                        .graduationTargetGpa(new BigDecimal("3.2"))
                        .courses(List.of())
                        .gradeOptions(List.of())
                        .build());
        mvc.perform(get("/api/v1/ai/what-if/snapshot"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.snapshotRef").value("a".repeat(64)))
                .andExpect(jsonPath("$.data.semesterTargetGpa").value(3.5))
                .andExpect(jsonPath("$.data.graduationTargetGpa").value(3.2));
        verify(service).findMySnapshot(principal);
    }

    @Test
    void numericAssumptionsBindWithoutASessionOrChatMessage() throws Exception {
        when(service.analyzeMySimulation(eq(principal), any()))
                .thenReturn(new GpaSimulationCommentResponse(
                        GpaSimulationPreviewResponse.builder()
                                .semesterGpa(new BigDecimal("3.25"))
                                .build(),
                        "Nhận xét tạm thời."));
        mvc.perform(post("/api/v1/ai/what-if/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"enrollmentId\":11,\"grade4\":2.5}")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.comment").value("Nhận xét tạm thời."))
                .andExpect(jsonPath("$.data.sessionId").doesNotExist())
                .andExpect(jsonPath("$.data.messageId").doesNotExist());
        var request = ArgumentCaptor.forClass(GpaSimulationRequest.class);
        verify(service).analyzeMySimulation(eq(principal), request.capture());
        assertThat(request.getValue().assumptions().getFirst().grade4()).isEqualByComparingTo("2.5");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"enrollmentId\":\"11\",\"grade4\":2}",
                "{\"enrollmentId\":11.5,\"grade4\":2}",
                "{\"enrollmentId\":true,\"grade4\":2}",
                "{\"enrollmentId\":11,\"grade4\":\"2\"}",
                "{\"enrollmentId\":11,\"grade4\":null}",
                "{\"enrollmentId\":11,\"grade4\":true}",
                "{\"enrollmentId\":11,\"grade4\":2,\"locked\":true}",
                "{\"enrollmentId\":11,\"grade4\":4.1}"
            })
    void rejectsCoercibleOrUnknownAssumptionFields(String assumption) throws Exception {
        mvc.perform(post("/api/v1/ai/what-if/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(assumption)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.errors[0].field").exists());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sessionId", "semesterId", "semesterTargetGpa", "graduationTargetGpa"})
    void rejectsClientIdentitySessionAndTargetFieldsEvenWhenNull(String field) throws Exception {
        String request = body("{\"enrollmentId\":11,\"grade4\":2}");
        request = request.substring(0, request.length() - 1) + ",\"" + field + "\":null}";
        mvc.perform(post("/api/v1/ai/what-if/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value(field));
        verifyNoInteractions(service);
    }

    @Test
    void staleSnapshotAndCapacityErrorsPreserveTheirHttpContract() throws Exception {
        when(service.previewMyGpa(eq(principal), any()))
                .thenThrow(new GpaSimulationException(ErrorCode.GPA_SIMULATION_SNAPSHOT_CHANGED));
        mvc.perform(post("/api/v1/ai/what-if/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"enrollmentId\":11,\"grade4\":2}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4801));
        when(service.analyzeMySimulation(eq(principal), any()))
                .thenThrow(new AiAssistantException(ErrorCode.AI_ADVISOR_CAPACITY_EXCEEDED));
        mvc.perform(post("/api/v1/ai/what-if/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"enrollmentId\":11,\"grade4\":2}")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "10"));
    }

    private String body(String assumption) {
        return "{\"snapshotRef\":\"" + "a".repeat(64) + "\",\"assumptions\":[" + assumption + "]}";
    }
}
