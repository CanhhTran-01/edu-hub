package com.eduhub.edu_hub.aiassistant.api.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eduhub.edu_hub.aiassistant.api.dto.request.*;
import com.eduhub.edu_hub.aiassistant.api.dto.response.*;
import com.eduhub.edu_hub.aiassistant.api.exception.AiAssistantExceptionHandler;
import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.aiassistant.application.service.*;
import com.eduhub.edu_hub.aiassistant.domain.enums.KnowledgeSourceStatus;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import com.eduhub.edu_hub.shared.exception.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AiControllersTest {
    AiChatService chat = mock(AiChatService.class);
    ChatHistoryService history = mock(ChatHistoryService.class);
    KnowledgeSourceService sources = mock(KnowledgeSourceService.class);
    Clock clock = Clock.fixed(Instant.parse("2026-11-18T17:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    Jwt principal = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("user")
            .claim("token_use", "ACCESS")
            .build();
    MockMvc mvc;

    @BeforeEach
    void configureControllers() {
        var responses = new ApiResponseFactory(clock);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(principal));
        mvc = MockMvcBuilders.standaloneSetup(
                        new AiChatController(responses, chat, history),
                        new KnowledgeSourceController(responses, sources))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new AiAssistantExceptionHandler(responses), new GlobalExceptionHandler(responses))
                .build();
    }

    @AfterEach
    void clearPrincipal() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadBindsMetadataAndReturnsAcceptedProcessingResponse() throws Exception {
        var response = new KnowledgeSourceResponse(
                8L,
                7L,
                null,
                "Quy chế",
                "Quy chế",
                "quy-che.txt",
                LocalDateTime.now(clock),
                LocalDate.of(2026, 1, 1),
                null,
                KnowledgeSourceStatus.PROCESSING,
                null,
                "gemini-embedding-001",
                "001",
                0);
        when(sources.upload(eq(principal), any())).thenReturn(response);
        mvc.perform(multipart("/api/v1/ai/documents")
                        .file(new MockMultipartFile("file", "quy-che.txt", "text/plain", new byte[] {1}))
                        .param("title", "Quy chế")
                        .param("documentType", "Quy chế")
                        .param("effectiveDate", "2026-01-01")
                        .param("uploadedBy", "999"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.uploadedBy").value(7))
                .andExpect(jsonPath("$.data.fileHash").doesNotExist());
        var request = ArgumentCaptor.forClass(UploadKnowledgeSourceRequest.class);
        verify(sources).upload(eq(principal), request.capture());
        assertThat(request.getValue().getEffectiveDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void sessionDetailUsesPrincipalAndDisablesBrowserCaching() throws Exception {
        when(history.getSessionById(principal, "3"))
                .thenReturn(new ChatSessionDetailResponse(
                        "3", "Học lại", "KNOWLEDGE", LocalDateTime.now(clock), List.of()));
        mvc.perform(get("/api/v1/ai/sessions/3").param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.title").value("Học lại"));
        verify(history).getSessionById(principal, "3");
    }

    @Test
    void sessionListBindsKeywordDateAndPaginationFilters() throws Exception {
        mvc.perform(get("/api/v1/ai/sessions")
                        .param("keyword", "học phí")
                        .param("fromDate", "2026-01-01")
                        .param("toDate", "2026-11-19")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk());
        var request = ArgumentCaptor.forClass(SearchChatSessionsRequest.class);
        verify(history).getAllSessions(eq(principal), request.capture());
        assertThat(request.getValue().getKeyword()).isEqualTo("học phí");
        assertThat(request.getValue().getToDate()).isEqualTo(LocalDate.of(2026, 11, 19));
        assertThat(request.getValue().getPage()).isEqualTo(1);
    }

    @Test
    void rateLimitReturnsBusinessCodeAndRetryHeader() throws Exception {
        when(chat.chat(eq(principal), any())).thenThrow(new AiAssistantException(ErrorCode.AI_RATE_LIMIT_EXCEEDED));
        mvc.perform(post("/api/v1/ai/chat")
                        .contentType("application/json")
                        .content(
                                """
                        {"message":"Quy chế học lại?"}
                        """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value(4606));
    }
}
