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
import com.eduhub.edu_hub.aiassistant.domain.enums.ChatMessageRole;
import com.eduhub.edu_hub.aiassistant.domain.enums.KnowledgeSourceStatus;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import com.eduhub.edu_hub.shared.exception.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
                        "3",
                        "Học lại",
                        "KNOWLEDGE",
                        LocalDateTime.now(clock),
                        List.of(new ChatHistoryMessageResponse(
                                "11",
                                ChatMessageRole.ASSISTANT,
                                "Theo quy chế [K21].",
                                LocalDateTime.now(clock),
                                List.of(citationResponse())))));
        mvc.perform(get("/api/v1/ai/sessions/3").param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.title").value("Học lại"))
                .andExpect(jsonPath("$.data.messages[0].intent").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.messages[0].citations[0].referenceCode")
                        .value("K21"))
                .andExpect(
                        jsonPath("$.data.messages[0].citations[0].sourceTitle").value("Quy chế"))
                .andExpect(
                        jsonPath("$.data.messages[0].citations[0].pageNumber").value(2))
                .andExpect(
                        jsonPath("$.data.messages[0].citations[0].sectionTitle").doesNotHaveJsonPath());
        verify(history).getSessionById(principal, "3");
    }

    @Test
    void chatReturnsSourceAndPageCitationsWithoutSectionTitles() throws Exception {
        when(chat.chat(eq(principal), any()))
                .thenReturn(ChatResponse.builder()
                        .sessionId("3")
                        .messageId("11")
                        .content("Theo quy chế [K21].")
                        .citations(List.of(citationResponse()))
                        .build());
        mvc.perform(
                        post("/api/v1/ai/chat")
                                .contentType("application/json")
                                .content(
                                        """
                                {"message":"Quy chế học lại?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.citations[0].referenceCode").value("K21"))
                .andExpect(jsonPath("$.data.citations[0].sourceTitle").value("Quy chế"))
                .andExpect(jsonPath("$.data.citations[0].pageNumber").value(2))
                .andExpect(jsonPath("$.data.citations[0].sectionTitle").doesNotHaveJsonPath());
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

    @Test
    void downloadReturnsBinaryFileWithUnicodeFilenameAndNoCache() throws Exception {
        byte[] fileBytes = "Quy chế đào tạo".getBytes(StandardCharsets.UTF_8);
        when(sources.downloadSourceFile(principal, 8L))
                .thenReturn(new KnowledgeSourceDownloadResponse("Quy chế.txt", "text/plain;charset=UTF-8", fileBytes));
        var response = mvc.perform(get("/api/v1/ai/documents/8/download"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(fileBytes))
                .andExpect(content().contentType("text/plain;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, fileBytes.length))
                .andReturn()
                .getResponse();
        var disposition = ContentDisposition.parse(response.getHeader(HttpHeaders.CONTENT_DISPOSITION));
        assertThat(disposition.isAttachment()).isTrue();
        assertThat(disposition.getFilename()).isEqualTo("Quy chế.txt");
        verify(sources).downloadSourceFile(principal, 8L);
    }

    @Test
    void downloadWithoutAnOriginalFileReturnsJsonErrorInsteadOfAnEmptyFile() throws Exception {
        when(sources.downloadSourceFile(principal, 8L))
                .thenThrow(new AiAssistantException(ErrorCode.KNOWLEDGE_SOURCE_FILE_NOT_FOUND));
        mvc.perform(get("/api/v1/ai/documents/8/download"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4610))
                .andExpect(jsonPath("$.success").value(false));
    }

    private CitationResponse citationResponse() {
        return CitationResponse.builder()
                .referenceCode("K21")
                .chunkId(21L)
                .sourceId(8L)
                .sourceTitle("Quy chế")
                .pageNumber(2)
                .snippet("Nội dung quy chế.")
                .sourceAvailable(true)
                .build();
    }
}
