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
import com.eduhub.edu_hub.aiassistant.domain.enums.AdvisorRequestState;
import com.eduhub.edu_hub.aiassistant.domain.enums.ChatMessageRole;
import com.eduhub.edu_hub.aiassistant.domain.enums.KnowledgeSourceStatus;
import com.eduhub.edu_hub.aiassistant.infrastructure.adapter.AiTokenCounter;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiAdvisorProperties;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    ChatHistoryService history = mock(ChatHistoryService.class);
    AiAdvisorChatService advisor = mock(AiAdvisorChatService.class);
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
                        new AiChatController(responses, advisor),
                        new AiChatSessionController(responses, history),
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
                        false,
                        LocalDateTime.now(clock),
                        List.of(new ChatHistoryMessageResponse(
                                "11",
                                ChatMessageRole.ASSISTANT,
                                "Theo quy chế [K21].",
                                LocalDateTime.now(clock),
                                List.of(citationResponse()),
                                null))));
        mvc.perform(get("/api/v1/ai/sessions/3").param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.title").value("Học lại"))
                .andExpect(jsonPath("$.data.pinned").value(false))
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
    void renameUsesThePrincipalAndReturnsTheSummaryWithTheNormalizedTitle() throws Exception {
        when(history.renameSession(eq(principal), eq("3"), any()))
                .thenReturn(new ChatSessionSummaryResponse(
                        "3",
                        "Học vụ cá nhân",
                        "KNOWLEDGE",
                        true,
                        LocalDateTime.now(clock),
                        LocalDateTime.now(clock),
                        "Tin cuối"));

        mvc.perform(patch("/api/v1/ai/sessions/3/title")
                        .param("userId", "999")
                        .contentType("application/json")
                        .content("{\"title\":\"  Ho\\u0323c   vu\\u0323  cá nhân  \"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Học vụ cá nhân"))
                .andExpect(jsonPath("$.data.pinned").value(true))
                .andExpect(jsonPath("$.data.lastMessage").value("Tin cuối"));
        var renameRequest = ArgumentCaptor.forClass(RenameChatSessionRequest.class);
        verify(history).renameSession(eq(principal), eq("3"), renameRequest.capture());
        assertThat(renameRequest.getValue().getTitle()).isEqualTo("Học vụ cá nhân");
        verifyNoInteractions(advisor, sources);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"title\":null}",
                "{\"title\":\"\"}",
                "{\"title\":\"   \"}",
                "{\"title\":123}",
                "{\"title\":true}",
                "{\"title\":[]}",
                "{\"title\":{}}",
                "{\"title\":\"Tên mới\",\"userId\":999}",
                "{\"title\":\"Tên mới\",\"pinned\":null}"
            })
    void renameRejectsMissingBlankNonStringAndUnsupportedFields(String requestBody) throws Exception {
        mvc.perform(patch("/api/v1/ai/sessions/3/title")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.errors[0].field").exists());
        verifyNoInteractions(history, advisor, sources);
    }

    @Test
    void renameValidatesTheTitleLengthAfterNormalization() throws Exception {
        String maximumTitle = "a".repeat(255);
        mvc.perform(patch("/api/v1/ai/sessions/3/title")
                        .contentType("application/json")
                        .content("{\"title\":\"  " + maximumTitle + "  \"}"))
                .andExpect(status().isOk());
        var renameRequest = ArgumentCaptor.forClass(RenameChatSessionRequest.class);
        verify(history).renameSession(eq(principal), eq("3"), renameRequest.capture());
        assertThat(renameRequest.getValue().getTitle()).isEqualTo(maximumTitle);

        mvc.perform(patch("/api/v1/ai/sessions/3/title")
                        .contentType("application/json")
                        .content("{\"title\":\"" + "a".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        verifyNoMoreInteractions(history);
    }

    @Test
    void renameRequiresARequestBodyAndPreservesTheNotFoundContract() throws Exception {
        mvc.perform(patch("/api/v1/ai/sessions/3/title").contentType("application/json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
        verifyNoInteractions(history);

        when(history.renameSession(eq(principal), eq("999"), any()))
                .thenThrow(new AiAssistantException(ErrorCode.CHAT_SESSION_NOT_FOUND));
        mvc.perform(patch("/api/v1/ai/sessions/999/title")
                        .contentType("application/json")
                        .content("{\"title\":\"Tên mới\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4602));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void pinUsesTheAuthenticatedPrincipalAndReturnsTheUpdatedSummary(boolean pinned) throws Exception {
        when(history.updateSessionPin(eq(principal), eq("3"), any()))
                .thenReturn(new ChatSessionSummaryResponse(
                        "3",
                        "Học lại",
                        "KNOWLEDGE",
                        pinned,
                        LocalDateTime.now(clock),
                        LocalDateTime.now(clock),
                        "Tin cuối"));

        mvc.perform(patch("/api/v1/ai/sessions/3/pin")
                        .param("userId", "999")
                        .contentType("application/json")
                        .content("{\"pinned\":" + pinned + "}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessionId").value("3"))
                .andExpect(jsonPath("$.data.pinned").value(pinned))
                .andExpect(jsonPath("$.data.lastMessage").value("Tin cuối"));
        var pinRequest = ArgumentCaptor.forClass(UpdateChatSessionPinRequest.class);
        verify(history).updateSessionPin(eq(principal), eq("3"), pinRequest.capture());
        assertThat(pinRequest.getValue().getPinned()).isEqualTo(pinned);
        verifyNoInteractions(advisor, sources);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"pinned\":null}",
                "{\"pinned\":\"true\"}",
                "{\"pinned\":\"false\"}",
                "{\"pinned\":0}",
                "{\"pinned\":1}",
                "{\"pinned\":[]}",
                "{\"pinned\":{}}",
                "{\"pinned\":true,\"userId\":999}",
                "{\"pinned\":false,\"title\":null}"
            })
    void pinRejectsMissingNullNonBooleanAndUnsupportedFields(String requestBody) throws Exception {
        mvc.perform(patch("/api/v1/ai/sessions/3/pin")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.errors[0].field").exists());
        verifyNoInteractions(history, advisor, sources);
    }

    @Test
    void pinRequiresARequestBody() throws Exception {
        mvc.perform(patch("/api/v1/ai/sessions/3/pin").contentType("application/json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
        verifyNoInteractions(history);
    }

    @Test
    void deleteUsesTheAuthenticatedPrincipalAndReturnsAnUncachedVoidResponse() throws Exception {
        mvc.perform(delete("/api/v1/ai/sessions/3").param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
        verify(history).deleteSession(principal, "3");
        verifyNoInteractions(advisor, sources);
    }

    @Test
    void pinAndDeleteReturnTheSameNotFoundContractForUnavailableSessions() throws Exception {
        when(history.updateSessionPin(eq(principal), eq("999"), any()))
                .thenThrow(new AiAssistantException(ErrorCode.CHAT_SESSION_NOT_FOUND));
        doThrow(new AiAssistantException(ErrorCode.CHAT_SESSION_NOT_FOUND))
                .when(history)
                .deleteSession(principal, "999");

        mvc.perform(patch("/api/v1/ai/sessions/999/pin")
                        .contentType("application/json")
                        .content("{\"pinned\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4602));
        mvc.perform(delete("/api/v1/ai/sessions/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4602));
    }

    @Test
    void sessionListExposesThePinnedFlagInEachSummary() throws Exception {
        var summary = new ChatSessionSummaryResponse(
                "3", "Học lại", "KNOWLEDGE", true, LocalDateTime.now(clock), LocalDateTime.now(clock), "Tin cuối");
        when(history.getAllSessions(eq(principal), any()))
                .thenReturn(PageResponse.<ChatSessionSummaryResponse>builder()
                        .content(List.of(summary))
                        .pageNumber(0)
                        .pageSize(20)
                        .totalElements(1)
                        .totalPages(1)
                        .isLast(true)
                        .build());

        mvc.perform(get("/api/v1/ai/sessions"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.content[0].pinned").value(true))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    void missingSessionReturnsAiJsonErrorForAClientAcceptingEventStream() throws Exception {
        when(history.getSessionById(principal, "999"))
                .thenThrow(new AiAssistantException(ErrorCode.CHAT_SESSION_NOT_FOUND));
        mvc.perform(get("/api/v1/ai/sessions/999").accept("text/event-stream"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value(4602))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.path").value("/api/v1/ai/sessions/999"));
        verify(history).getSessionById(principal, "999");
        verifyNoInteractions(advisor);
    }

    @Test
    void advisorCompletionReturnsSavedContentAndCitationsWithinTheSelectedSession() throws Exception {
        UUID clientRequestId = UUID.randomUUID();
        var execution = new AdvisorChatExecution(
                clientRequestId,
                7L,
                principal,
                "Quy chế học lại?",
                new ChatHistoryService.ConversationContext(3L, 11L, List.of()),
                new AiAdvisorProperties(),
                new AiTokenCounter(),
                clock);
        var savedResponse = ChatResponse.builder()
                .sessionId("3")
                .messageId("13")
                .content("Theo quy chế [K21].")
                .citations(List.of(citationResponse()))
                .build();
        when(advisor.prepareChat(eq(principal), any())).thenReturn(execution);
        doAnswer(invocation -> {
                    execution.complete(() -> savedResponse);
                    return null;
                })
                .when(advisor)
                .startChat(execution);

        var stream = mvc.perform(post("/api/v1/ai/chat/stream")
                        .contentType("application/json")
                        .accept("text/event-stream")
                        .content("{\"sessionId\":\"3\",\"message\":\"Quy chế học lại?\",\"clientRequestId\":\""
                                + clientRequestId + "\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        var body = mvc.perform(asyncDispatch(stream))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(body)
                .contains(
                        "event:agent.completed",
                        "\"sessionId\":\"3\"",
                        "\"messageId\":\"13\"",
                        "\"content\":\"Theo quy chế [K21].\"",
                        "\"referenceCode\":\"K21\"",
                        "\"sourceTitle\":\"Quy chế\"",
                        "\"pageNumber\":2")
                .doesNotContain("\"intent\":", "\"sectionTitle\":");
        var captured = ArgumentCaptor.forClass(AdvisorChatRequest.class);
        verify(advisor).prepareChat(eq(principal), captured.capture());
        assertThat(captured.getValue().getSessionId()).isEqualTo("3");
    }

    @Test
    void removedSynchronousChatEndpointReturnsNotFoundWithoutStartingAnAdvisorRequest() throws Exception {
        mvc.perform(post("/api/v1/ai/chat")
                        .contentType("application/json")
                        .content("{\"message\":\"Quy chế học lại?\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.ENDPOINT_NOT_FOUND.getCode()));
        verifyNoInteractions(advisor, history);
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
    void advisorRateLimitReturnsBusinessCodeAndRetryHeader() throws Exception {
        when(advisor.prepareChat(eq(principal), any()))
                .thenThrow(new AiAssistantException(ErrorCode.AI_RATE_LIMIT_EXCEEDED));
        mvc.perform(post("/api/v1/ai/chat/stream")
                        .contentType("application/json")
                        .accept("text/event-stream")
                        .content(
                                "{\"message\":\"Quy chế học lại?\",\"clientRequestId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value(4606));
    }

    @Test
    void advisorStreamUsesSseEnvelopeAndVerifiedPrincipal() throws Exception {
        UUID clientRequestId = UUID.randomUUID();
        var execution = new AdvisorChatExecution(
                clientRequestId,
                7L,
                principal,
                "GPA của tôi?",
                new ChatHistoryService.ConversationContext(null, null, List.of()),
                new AiAdvisorProperties(),
                new AiTokenCounter(),
                clock);
        when(advisor.prepareChat(eq(principal), any())).thenReturn(execution);
        doAnswer(invocation -> {
                    execution.publish("agent.started", Map.of("clientRequestId", clientRequestId));
                    execution.cancel("USER_REQUEST");
                    return null;
                })
                .when(advisor)
                .startChat(execution);

        var stream = mvc.perform(post("/api/v1/ai/chat/stream")
                        .contentType("application/json")
                        .accept("text/event-stream")
                        .content("{\"message\":\"GPA của tôi?\",\"clientRequestId\":\"" + clientRequestId + "\"}"))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andReturn();
        var body = mvc.perform(asyncDispatch(stream))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(body)
                .contains(
                        "event:agent.started",
                        "event:agent.cancelled",
                        "id:1",
                        "id:2",
                        "\"requestId\":\"" + execution.getRequestId(),
                        "\"sequence\":1")
                .doesNotContain("thoughtSignatures", "systemPrompt");
        var captured = ArgumentCaptor.forClass(AdvisorChatRequest.class);
        verify(advisor).prepareChat(eq(principal), captured.capture());
        assertThat(captured.getValue().getClientRequestId()).isEqualTo(clientRequestId);
    }

    @Test
    void advisorRejectsMissingRequestIdentityAndArbitraryStudentIdBeforeOpeningStream() throws Exception {
        mvc.perform(post("/api/v1/ai/chat/stream")
                        .contentType("application/json")
                        .accept("text/event-stream")
                        .content("{\"message\":\"GPA của tôi?\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
        mvc.perform(post("/api/v1/ai/chat/stream")
                        .contentType("application/json")
                        .accept("text/event-stream")
                        .content("{\"message\":\"GPA?\",\"clientRequestId\":\"" + UUID.randomUUID()
                                + "\",\"studentId\":999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
        verifyNoInteractions(advisor);
    }

    @Test
    void advisorCapacityFailureIsJsonBeforeAnySseAndProvidesRetryDelay() throws Exception {
        when(advisor.prepareChat(eq(principal), any()))
                .thenThrow(new AiAssistantException(ErrorCode.AI_ADVISOR_CAPACITY_EXCEEDED));
        mvc.perform(post("/api/v1/ai/chat/stream")
                        .contentType("application/json")
                        .accept("text/event-stream")
                        .content("{\"message\":\"GPA?\",\"clientRequestId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "10"))
                .andExpect(jsonPath("$.code").value(4613));
        verify(advisor, never()).startChat(any());
    }

    @Test
    void advisorStatusAndCancelUsePrincipalAndDisableCaching() throws Exception {
        UUID requestId = UUID.randomUUID();
        var response = new AdvisorRequestStatusResponse(
                requestId,
                UUID.randomUUID(),
                null,
                AdvisorRequestState.CANCELLED,
                LocalDateTime.now(clock),
                LocalDateTime.now(clock),
                2,
                1,
                0,
                2000,
                "USER_REQUEST",
                null,
                null,
                null);
        when(advisor.findRequestStatus(principal, requestId)).thenReturn(response);
        when(advisor.cancelRequest(principal, requestId)).thenReturn(response);
        mvc.perform(get("/api/v1/ai/chat-requests/" + requestId).param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.data.state").value("CANCELLED"));
        mvc.perform(post("/api/v1/ai/chat-requests/" + requestId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.stopReason").value("USER_REQUEST"));
        verify(advisor).findRequestStatus(principal, requestId);
        verify(advisor).cancelRequest(principal, requestId);
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
