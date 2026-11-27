package com.eduhub.edu_hub.aiassistant.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.academic.api.dto.request.GpaGoalRequest.CourseGradeConditionRequest;
import com.eduhub.edu_hub.academic.api.dto.response.*;
import com.eduhub.edu_hub.academic.domain.enums.*;
import com.eduhub.edu_hub.aiassistant.api.dto.request.RenameChatSessionRequest;
import com.eduhub.edu_hub.aiassistant.api.dto.request.SearchChatSessionsRequest;
import com.eduhub.edu_hub.aiassistant.api.dto.request.UpdateChatSessionPinRequest;
import com.eduhub.edu_hub.aiassistant.api.dto.response.AdvisorMessagePayloadResponse;
import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.aiassistant.application.port.AiAdvisorModelPort.ConversationMessage;
import com.eduhub.edu_hub.aiassistant.application.port.KnowledgeChunkPort;
import com.eduhub.edu_hub.aiassistant.application.port.KnowledgeChunkPort.RetrievedChunk;
import com.eduhub.edu_hub.aiassistant.domain.enums.*;
import com.eduhub.edu_hub.aiassistant.domain.model.*;
import com.eduhub.edu_hub.aiassistant.domain.repository.*;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiRagProperties;
import com.eduhub.edu_hub.aiassistant.infrastructure.serialization.AdvisorMessageContentCodec;
import com.eduhub.edu_hub.enrollment.api.dto.response.MinimumExamScoreResponse;
import com.eduhub.edu_hub.enrollment.domain.enums.MinimumExamScoreStatus;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.scheduling.api.dto.response.SemesterResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.json.JsonMapper;

class ChatHistoryServiceTest {
    AiAccessService accessService = mock(AiAccessService.class);
    ChatSessionRepository sessions = mock(ChatSessionRepository.class);
    ChatMessageRepository messages = mock(ChatMessageRepository.class);
    MessageChunkReferenceRepository references = mock(MessageChunkReferenceRepository.class);
    KnowledgeChunkPort chunks = mock(KnowledgeChunkPort.class);
    AiRagProperties properties = new AiRagProperties();
    AdvisorMessageContentCodec codec = new AdvisorMessageContentCodec(
            JsonMapper.builder().findAndAddModules().build());
    Clock clock = Clock.fixed(Instant.parse("2026-11-18T17:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    ChatHistoryService service =
            new ChatHistoryService(accessService, sessions, messages, references, chunks, properties, clock, codec);
    Jwt principal = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("student")
            .build();
    ChatSession session = new ChatSession(7L, "Học lại", LocalDateTime.of(2026, 11, 18, 10, 0));

    @BeforeEach
    void prepareHistory() {
        session.setId(3L);
        properties.setFallbackMessage("Chưa đủ căn cứ.");
        when(accessService.requireStudent(principal))
                .thenReturn(UserInfoResponse.builder().id(7L).build());
    }

    @Test
    void foreignSessionReturnsNotFoundWithoutReadingMessages() {
        when(sessions.findByIdAndUserIdAndDeletedFalse(3L, 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getSessionById(principal, "3"))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_SESSION_NOT_FOUND));
        verifyNoInteractions(messages, references);
    }

    @Test
    void sessionListPrioritizesPinsBeforePaginationAndReturnsDecodedPreview() {
        session.updatePinned(true);
        var searchRequest = new SearchChatSessionsRequest();
        searchRequest.setPage(1);
        searchRequest.setSize(10);
        when(sessions.findAll(ArgumentMatchers.<Specification<ChatSession>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(session), PageRequest.of(1, 10), 11));
        var lastMessage = mock(ChatMessageRepository.LastMessageProjection.class);
        when(lastMessage.getSessionId()).thenReturn(3L);
        when(lastMessage.getContent()).thenReturn(codec.encode(payload(false)));
        when(messages.findLastMessages(7L, List.of(3L))).thenReturn(List.of(lastMessage));

        var response = service.getAllSessions(principal, searchRequest);

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(sessions).findAll(ArgumentMatchers.<Specification<ChatSession>>any(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getSort())
                .containsExactly(Sort.Order.desc("pinned"), Sort.Order.desc("startedAt"), Sort.Order.desc("id"));
        assertThat(response.getContent().getFirst().pinned()).isTrue();
        assertThat(response.getContent().getFirst().lastMessage())
                .isEqualTo(payload(false).text());
        assertThat(response.getTotalElements()).isEqualTo(11);
    }

    @Test
    void renameNormalizesTheTitleAndPreservesMessagesPinAndConversationDates() {
        session.updatePinned(true);
        when(sessions.findOwnedForUpdate(3L, 7L)).thenReturn(Optional.of(session));
        var renameRequest = new RenameChatSessionRequest();
        renameRequest.setTitle("  Ho\u0323c   vu\u0323 \n cá nhân  ");
        var lastMessage = mock(ChatMessageRepository.LastMessageProjection.class);
        when(lastMessage.getContent()).thenReturn(codec.encode(payload(false)));
        when(messages.findLastMessages(7L, List.of(3L))).thenReturn(List.of(lastMessage));
        LocalDateTime startedAt = session.getStartedAt();
        LocalDateTime lastMessageAt = session.getLastMessageAt();

        var response = service.renameSession(principal, "3", renameRequest);

        assertThat(session.getTitle()).isEqualTo("Học vụ cá nhân");
        assertThat(response.title()).isEqualTo("Học vụ cá nhân");
        assertThat(response.sessionId()).isEqualTo("3");
        assertThat(response.pinned()).isTrue();
        assertThat(response.startedAt()).isEqualTo(startedAt);
        assertThat(response.lastMessageAt()).isEqualTo(lastMessageAt);
        assertThat(response.lastMessage()).isEqualTo(payload(false).text());
        verify(sessions).findOwnedForUpdate(3L, 7L);
        verify(messages, never()).save(any());
        verifyNoInteractions(references, chunks);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 255})
    void renameAcceptsTheTitleLengthBoundaries(int titleLength) {
        when(sessions.findOwnedForUpdate(3L, 7L)).thenReturn(Optional.of(session));
        var renameRequest = new RenameChatSessionRequest();
        String title = "a".repeat(titleLength);
        renameRequest.setTitle("  " + title + "  ");

        assertThat(service.renameSession(principal, "3", renameRequest).title()).isEqualTo(title);
        assertThat(session.getTitle()).isEqualTo(title);
    }

    @Test
    void renameRejectsMissingBlankAndOversizedTitlesBeforeLockingTheSession() {
        var blankRequest = new RenameChatSessionRequest();
        blankRequest.setTitle(" \t\n ");
        var oversizedRequest = new RenameChatSessionRequest();
        oversizedRequest.setTitle("a".repeat(256));

        for (RenameChatSessionRequest invalidRequest :
                Arrays.asList(null, new RenameChatSessionRequest(), blankRequest, oversizedRequest)) {
            assertThatThrownBy(() -> service.renameSession(principal, "3", invalidRequest))
                    .isInstanceOfSatisfying(AiAssistantException.class, exception -> {
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                        assertThat(exception.getFieldErrors().getFirst().getField())
                                .isEqualTo("title");
                    });
        }
        assertThat(session.getTitle()).isEqualTo("Học lại");
        verifyNoInteractions(sessions, messages, references, chunks);
    }

    @Test
    void repeatingTheNormalizedTitlePreservesAuditMetadata() {
        when(sessions.findOwnedForUpdate(3L, 7L)).thenReturn(Optional.of(session));
        LocalDateTime updatedAt = session.getStartedAt().plusHours(1);
        session.setUpdatedAt(updatedAt);
        session.setUpdatedBy("7");
        var renameRequest = new RenameChatSessionRequest();
        renameRequest.setTitle("  Học   lại  ");

        service.renameSession(principal, "3", renameRequest);

        assertThat(session.getTitle()).isEqualTo("Học lại");
        assertThat(session.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(session.getUpdatedBy()).isEqualTo("7");
        verify(sessions, never()).save(any());
        verify(messages, never()).save(any());
    }

    @Test
    void renamingDuringGenerationKeepsTheTitleAndAllowsTheExistingTurnToBeSaved() {
        when(sessions.findOwnedForUpdate(3L, 7L)).thenReturn(Optional.of(session));
        when(messages.findBySessionIdAndSessionUserIdAndSessionDeletedFalseAndDeletedFalseOrderByIdDesc(
                        eq(3L), eq(7L), any()))
                .thenReturn(List.of(message(11L, "Tin trước khi đổi tên")));
        when(messages.save(any())).thenAnswer(invocation -> {
            ChatMessage savedMessage = invocation.getArgument(0);
            savedMessage.setId(99L);
            return savedMessage;
        });
        var conversationBeforeRename = new ChatHistoryService.ConversationContext(3L, 11L, List.of());
        var renameRequest = new RenameChatSessionRequest();
        renameRequest.setTitle("Tên người dùng đã chọn");
        service.renameSession(principal, "3", renameRequest);

        var response = service.recordExchange(
                principal, conversationBeforeRename, "Hỏi tiếp", "Câu trả lời đến sau đổi tên", List.of());

        assertThat(response.getSessionId()).isEqualTo("3");
        assertThat(session.getTitle()).isEqualTo("Tên người dùng đã chọn");
        verify(messages, times(2)).save(any());
        verify(sessions, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void pinChangesOnlyTheOwnedSessionState(boolean pinned) {
        session.updatePinned(!pinned);
        when(sessions.findOwnedForUpdate(3L, 7L)).thenReturn(Optional.of(session));
        var pinRequest = new UpdateChatSessionPinRequest();
        pinRequest.setPinned(pinned);
        LocalDateTime startedAt = session.getStartedAt();
        LocalDateTime lastMessageAt = session.getLastMessageAt();

        var response = service.updateSessionPin(principal, "3", pinRequest);

        assertThat(session.isPinned()).isEqualTo(pinned);
        assertThat(response.pinned()).isEqualTo(pinned);
        assertThat(response.sessionId()).isEqualTo("3");
        assertThat(response.startedAt()).isEqualTo(startedAt);
        assertThat(response.lastMessageAt()).isEqualTo(lastMessageAt);
        assertThat(session.isDeleted()).isFalse();
        verify(messages).findLastMessages(7L, List.of(3L));
        verify(messages, never()).save(any());
        verifyNoInteractions(references, chunks);
    }

    @Test
    void repeatingTheSamePinStatePreservesSessionMetadata() {
        session.updatePinned(true);
        LocalDateTime updatedAt = session.getStartedAt().plusHours(1);
        session.setUpdatedAt(updatedAt);
        session.setUpdatedBy("7");
        when(sessions.findOwnedForUpdate(3L, 7L)).thenReturn(Optional.of(session));
        var pinRequest = new UpdateChatSessionPinRequest();
        pinRequest.setPinned(true);

        service.updateSessionPin(principal, "3", pinRequest);
        service.updateSessionPin(principal, "3", pinRequest);

        assertThat(session.isPinned()).isTrue();
        assertThat(session.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(session.getUpdatedBy()).isEqualTo("7");
        verify(sessions, never()).save(any());
        verify(messages, never()).save(any());
    }

    @Test
    void deleteSoftDeletesOnlyTheOwnedSessionAndKeepsStoredHistory() {
        session.updatePinned(true);
        when(sessions.findOwnedForUpdate(3L, 7L)).thenReturn(Optional.of(session));
        ChatMessage storedAnswer = message(11L, "Nội dung lịch sử được giữ nguyên.");
        LocalDateTime lastMessageAt = session.getLastMessageAt();

        service.deleteSession(principal, "3");

        assertThat(session.isDeleted()).isTrue();
        assertThat(session.isPinned()).isTrue();
        assertThat(session.getTitle()).isEqualTo("Học lại");
        assertThat(session.getLastMessageAt()).isEqualTo(lastMessageAt);
        assertThat(storedAnswer.getContent()).isEqualTo("Nội dung lịch sử được giữ nguyên.");
        assertThat(storedAnswer.isDeleted()).isFalse();
        verify(sessions).findOwnedForUpdate(3L, 7L);
        verify(sessions, never()).delete(any(ChatSession.class));
        verify(sessions, never()).deleteById(any());
        verifyNoInteractions(messages, references, chunks);
    }

    @ParameterizedTest
    @ValueSource(strings = {"3", "99", "khong-phai-id", "9223372036854775808"})
    void sessionUpdatesHideUnavailableOrForeignSessions(String sessionId) {
        var pinRequest = new UpdateChatSessionPinRequest();
        pinRequest.setPinned(true);
        var renameRequest = new RenameChatSessionRequest();
        renameRequest.setTitle("Tên mới");

        assertThatThrownBy(() -> service.renameSession(principal, sessionId, renameRequest))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_SESSION_NOT_FOUND));
        assertThatThrownBy(() -> service.updateSessionPin(principal, sessionId, pinRequest))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_SESSION_NOT_FOUND));
        assertThatThrownBy(() -> service.deleteSession(principal, sessionId))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_SESSION_NOT_FOUND));
        verifyNoInteractions(messages, references, chunks);
        assertThat(session.isDeleted()).isFalse();
        assertThat(session.isPinned()).isFalse();
    }

    @Test
    void pinRequiresAnExplicitBooleanEvenWhenCalledWithoutAController() {
        assertThatThrownBy(() -> service.updateSessionPin(principal, "3", null))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.updateSessionPin(principal, "3", new UpdateChatSessionPinRequest()))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        verifyNoInteractions(sessions, messages, references, chunks);
    }

    @Test
    void mutationsRequireAnAuthenticatedStudentBeforeAccessingSessions() {
        when(accessService.requireStudent(null)).thenThrow(new AiAssistantException(ErrorCode.UNAUTHENTICATED));
        var pinRequest = new UpdateChatSessionPinRequest();
        pinRequest.setPinned(true);
        var renameRequest = new RenameChatSessionRequest();
        renameRequest.setTitle("Tên mới");

        assertThatThrownBy(() -> service.renameSession(null, "3", renameRequest))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHENTICATED));
        assertThatThrownBy(() -> service.updateSessionPin(null, "3", pinRequest))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHENTICATED));
        assertThatThrownBy(() -> service.deleteSession(null, "3"))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHENTICATED));
        verifyNoInteractions(sessions, messages, references, chunks);
    }

    @Test
    void deletedSessionCannotBeReadUpdatedOrReceiveACompletedTurn() {
        when(sessions.findOwnedForUpdate(3L, 7L))
                .thenReturn(Optional.of(session))
                .thenReturn(Optional.empty());
        var contextBeforeDeletion = new ChatHistoryService.ConversationContext(3L, 11L, List.of());
        var pinRequest = new UpdateChatSessionPinRequest();
        pinRequest.setPinned(true);
        var renameRequest = new RenameChatSessionRequest();
        renameRequest.setTitle("Tên mới");
        service.deleteSession(principal, "3");

        assertThatThrownBy(() -> service.renameSession(principal, "3", renameRequest))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_SESSION_NOT_FOUND));
        assertThatThrownBy(() -> service.getSessionById(principal, "3"))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_SESSION_NOT_FOUND));
        assertThatThrownBy(() -> service.loadConversation(7L, "3"))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_SESSION_NOT_FOUND));
        assertThatThrownBy(() -> service.updateSessionPin(principal, "3", pinRequest))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_SESSION_NOT_FOUND));
        assertThatThrownBy(() -> service.deleteSession(principal, "3"))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_SESSION_NOT_FOUND));
        assertThatThrownBy(() -> service.recordExchange(
                        principal, contextBeforeDeletion, "Câu hỏi đang chạy", "Kết quả đến muộn", List.of()))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_SESSION_NOT_FOUND));
        verifyNoInteractions(messages, references, chunks);
    }

    @Test
    void historyKeepsStoredAnswerButHidesSnippetOfUnavailableSource() {
        when(sessions.findByIdAndUserIdAndDeletedFalse(3L, 7L)).thenReturn(Optional.of(session));
        ChatMessage answer = message(11L, "Câu trả lời đã lưu [K21].");
        when(messages.findBySessionIdAndSessionUserIdAndSessionDeletedFalseAndDeletedFalseOrderByIdAsc(3L, 7L))
                .thenReturn(List.of(answer));
        var reference = mock(MessageChunkReferenceRepository.ReferenceProjection.class);
        when(reference.getMessageId()).thenReturn(11L);
        when(reference.getChunkId()).thenReturn(21L);
        when(reference.getSourceTitle()).thenReturn("Quy chế cũ");
        when(reference.getSnippet()).thenReturn("Nội dung đã ngừng sử dụng");
        when(reference.getSourceAvailable()).thenReturn(false);
        when(references.findOwnedReferences(eq(7L), eq(List.of(11L)), any())).thenReturn(List.of(reference));
        var detail = service.getSessionById(principal, "3");
        assertThat(detail.messages().getFirst().content()).isEqualTo(answer.getContent());
        var citation = detail.messages().getFirst().citations().getFirst();
        assertThat(citation.isSourceAvailable()).isFalse();
        assertThat(citation.getSourceTitle()).isEqualTo("Quy chế cũ");
        assertThat(citation.getSnippet()).isNull();
        verify(messages, never()).save(any());
    }

    @Test
    void staleConversationDoesNotWritePartialTurn() {
        when(sessions.findOwnedForUpdate(3L, 7L)).thenReturn(Optional.of(session));
        when(messages.findBySessionIdAndSessionUserIdAndSessionDeletedFalseAndDeletedFalseOrderByIdDesc(
                        eq(3L), eq(7L), any()))
                .thenReturn(List.of(message(12L, "Tin mới")));
        var context = new ChatHistoryService.ConversationContext(3L, 11L, List.of());
        assertThatThrownBy(() -> service.recordExchange(principal, context, "Hỏi tiếp", "Trả lời", List.of()))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.CHAT_SESSION_CHANGED));
        verify(messages, never()).save(any());
    }

    @Test
    void sourceDisabledAfterGenerationBecomesFallbackWithoutReference() {
        when(sessions.findOwnedForUpdate(3L, 7L)).thenReturn(Optional.of(session));
        when(messages.findBySessionIdAndSessionUserIdAndSessionDeletedFalseAndDeletedFalseOrderByIdDesc(
                        eq(3L), eq(7L), any()))
                .thenReturn(List.of());
        when(chunks.lockAvailableChunkIds(eq(List.of(21L)), any())).thenReturn(Set.of());
        when(messages.save(any())).thenAnswer(invocation -> {
            ChatMessage savedMessage = invocation.getArgument(0);
            savedMessage.setId(99L);
            return savedMessage;
        });
        var chunk = new RetrievedChunk(21L, 5L, "Quy chế", null, "Nội dung", 10, 0.9, LocalDate.of(2026, 1, 1));
        var response = service.recordExchange(
                principal,
                new ChatHistoryService.ConversationContext(3L, null, List.of()),
                "Học lại?",
                "Theo quy chế [K21].",
                List.of(chunk));
        assertThat(response.getContent()).isEqualTo("Chưa đủ căn cứ.");
        assertThat(response.getCitations()).isEmpty();
        verify(references, never()).saveAll(any());
    }

    @Test
    void memoryKeepsUserQuestionAndOnlyAnswersWithAllReferencesAvailable() {
        when(sessions.findByIdAndUserIdAndDeletedFalse(3L, 7L)).thenReturn(Optional.of(session));
        var userQuestion = new ChatMessage(session, ChatMessageRole.USER, "Quy chế học lại?", LocalDateTime.now(clock));
        userQuestion.setId(10L);
        var answerWithoutReferences = message(11L, "Chưa đủ căn cứ.");
        var answerWithUnavailableSource = message(12L, "Quy chế cũ [K21] [K22].");
        var answerWithAvailableSource = message(13L, "Quy chế hiện hành [K23].");
        when(messages.findBySessionIdAndSessionUserIdAndSessionDeletedFalseAndDeletedFalseOrderByIdDesc(
                        eq(3L), eq(7L), any()))
                .thenReturn(List.of(
                        answerWithAvailableSource, answerWithUnavailableSource, answerWithoutReferences, userQuestion));
        var availableOldReference = mock(MessageChunkReferenceRepository.ReferenceProjection.class);
        when(availableOldReference.getMessageId()).thenReturn(12L);
        when(availableOldReference.getSourceAvailable()).thenReturn(true);
        var unavailableReference = mock(MessageChunkReferenceRepository.ReferenceProjection.class);
        when(unavailableReference.getMessageId()).thenReturn(12L);
        when(unavailableReference.getSourceAvailable()).thenReturn(false);
        var availableReference = mock(MessageChunkReferenceRepository.ReferenceProjection.class);
        when(availableReference.getMessageId()).thenReturn(13L);
        when(availableReference.getSourceAvailable()).thenReturn(true);
        when(references.findOwnedReferences(eq(7L), eq(List.of(13L, 12L, 11L, 10L)), any()))
                .thenReturn(List.of(availableOldReference, unavailableReference, availableReference));

        var context = service.loadConversation(7L, "3");

        assertThat(context.lastMessageId()).isEqualTo(13L);
        assertThat(context.messages())
                .containsExactly(
                        new ConversationMessage("USER", userQuestion.getContent()),
                        new ConversationMessage("ASSISTANT", answerWithAvailableSource.getContent()));
        verify(messages, never()).save(any());
    }

    private ChatMessage message(Long id, String content) {
        var message = new ChatMessage(session, ChatMessageRole.ASSISTANT, content, LocalDateTime.now(clock));
        message.setId(id);
        return message;
    }

    @Test
    void structuredGpaRoundTripsThroughStorageHistoryAndFollowUpWithoutRagCitations() {
        when(sessions.findOwnedForUpdate(3L, 7L)).thenReturn(Optional.of(session));
        when(sessions.findByIdAndUserIdAndDeletedFalse(3L, 7L)).thenReturn(Optional.of(session));
        when(messages.save(any())).thenAnswer(invocation -> {
            ChatMessage saved = invocation.getArgument(0);
            saved.setId(saved.getRole() == ChatMessageRole.USER ? 10L : 11L);
            return saved;
        });
        var payload = payload(false);
        var response = service.recordExchange(
                principal,
                new ChatHistoryService.ConversationContext(3L, null, List.of()),
                "X đúng B",
                payload.text(),
                List.of(),
                payload);
        var savedMessages = org.mockito.ArgumentCaptor.forClass(ChatMessage.class);
        verify(messages, times(2)).save(savedMessages.capture());
        var savedAnswer = savedMessages.getAllValues().getLast();
        assertThat(savedAnswer.getContent()).contains("\"schemaVersion\":1", "\"gpaGoal\":{");
        assertThat(response.getContent()).isEqualTo(payload.text());
        when(messages.findBySessionIdAndSessionUserIdAndSessionDeletedFalseAndDeletedFalseOrderByIdAsc(3L, 7L))
                .thenReturn(savedMessages.getAllValues());
        when(messages.findBySessionIdAndSessionUserIdAndSessionDeletedFalseAndDeletedFalseOrderByIdDesc(
                        eq(3L), eq(7L), any()))
                .thenReturn(List.of(savedAnswer, savedMessages.getAllValues().getFirst()));

        var detail = service.getSessionById(principal, "3");
        var context = service.loadConversation(7L, "3");

        assertThat(detail.messages().getLast().content()).isEqualTo(payload.text());
        assertThat(detail.messages().getLast().payload()).isEqualTo(response.getPayload());
        assertThat(context.messages()).contains(new ConversationMessage("ASSISTANT", payload.text()));
        assertThat(context.latestGpaGoal()).isEqualTo(response.getPayload().gpaGoal());
        assertThat(context.latestGpaGoal().conditions().getFirst().gradeLetter())
                .isEqualTo("B");
    }

    @Test
    void latestGpaConditionsAreLoadedEvenWhenTheResultIsOutsideTheRecentMessageWindow() {
        when(sessions.findByIdAndUserIdAndDeletedFalse(3L, 7L)).thenReturn(Optional.of(session));
        when(messages.findBySessionIdAndSessionUserIdAndSessionDeletedFalseAndDeletedFalseOrderByIdDesc(
                        eq(3L), eq(7L), any()))
                .thenReturn(List.of(message(99L, "Tin gần đây không chứa phương án.")));
        when(messages.findAcademicContextMessages(eq(3L), eq(7L), eq("%\"gpaGoal\":{%"), any()))
                .thenReturn(List.of(message(11L, codec.encode(payload(false)))));
        var context = service.loadConversation(7L, "3");
        assertThat(context.lastMessageId()).isEqualTo(99L);
        assertThat(context.latestGpaGoal().conditions()).hasSize(1);
        assertThat(context.latestGpaGoal().snapshot().semester().getId()).isEqualTo(2L);
    }

    @Test
    void clarificationWithoutCitationsRemainsAvailableForTheNextMessage() {
        when(sessions.findByIdAndUserIdAndDeletedFalse(3L, 7L)).thenReturn(Optional.of(session));
        var clarification = new AdvisorMessagePayloadResponse(
                1, AdvisorMessagePayloadResponse.TYPE, "Bạn muốn đúng B hay từ B trở lên?", true, null, null);
        when(messages.findBySessionIdAndSessionUserIdAndSessionDeletedFalseAndDeletedFalseOrderByIdDesc(
                        eq(3L), eq(7L), any()))
                .thenReturn(List.of(message(11L, codec.encode(clarification))));
        assertThat(service.loadConversation(7L, "3").messages())
                .containsExactly(new ConversationMessage("ASSISTANT", clarification.text()));
    }

    @Test
    void unavailableRagSourceDoesNotDiscardTheVerifiedGpaPayloadDuringCommit() {
        when(sessions.findOwnedForUpdate(3L, 7L)).thenReturn(Optional.of(session));
        when(messages.save(any())).thenAnswer(invocation -> {
            ChatMessage saved = invocation.getArgument(0);
            saved.setId(11L);
            return saved;
        });
        var chunk = new RetrievedChunk(21L, 5L, "Quy chế", null, "Nội dung", 10, 0.9, LocalDate.of(2026, 1, 1));
        when(chunks.lockAvailableChunkIds(eq(List.of(21L)), any())).thenReturn(Set.of());
        var response = service.recordExchange(
                principal,
                new ChatHistoryService.ConversationContext(3L, null, List.of()),
                "Tìm GPA và quy chế",
                payload(false).text() + "\nTài liệu [K21]",
                List.of(chunk),
                payload(false));
        assertThat(response.getContent()).isEqualTo(payload(false).text()).doesNotContain("K21");
        assertThat(response.getPayload().gpaGoal()).isNotNull();
        assertThat(response.getCitations()).isEmpty();
    }

    private AdvisorMessagePayloadResponse payload(boolean clarification) {
        var snapshot = GpaSimulationSnapshotResponse.builder()
                .snapshotRef("a".repeat(64))
                .semester(SemesterResponse.builder()
                        .id(2L)
                        .semesterName("Kỳ cũ vẫn đọc được")
                        .build())
                .build();
        var goal = GpaGoalResponse.builder()
                .status(GpaGoalStatus.NEEDS_CLARIFICATION)
                .snapshot(snapshot)
                .conditions(List.of(new CourseGradeConditionRequest("X", 1L, CourseGradeComparison.EXACT, "B")))
                .pendingConditions(List.of())
                .calculatedAt(LocalDateTime.now(clock))
                .message("Kết quả đã kiểm tra.")
                .build();
        return new AdvisorMessagePayloadResponse(
                1, AdvisorMessagePayloadResponse.TYPE, "Phương án GPA đã lưu.", clarification, null, goal);
    }

    @Test
    void userTextCannotImpersonateAnAssistantGpaPayload() {
        when(sessions.findByIdAndUserIdAndDeletedFalse(3L, 7L)).thenReturn(Optional.of(session));
        var question =
                new ChatMessage(session, ChatMessageRole.USER, codec.encode(payload(false)), LocalDateTime.now(clock));
        question.setId(10L);
        when(messages.findBySessionIdAndSessionUserIdAndSessionDeletedFalseAndDeletedFalseOrderByIdAsc(3L, 7L))
                .thenReturn(List.of(question));
        when(messages.findBySessionIdAndSessionUserIdAndSessionDeletedFalseAndDeletedFalseOrderByIdDesc(
                        eq(3L), eq(7L), any()))
                .thenReturn(List.of(question));
        var detail = service.getSessionById(principal, "3");
        assertThat(detail.messages().getFirst().content()).isEqualTo(question.getContent());
        assertThat(detail.messages().getFirst().payload()).isNull();
        assertThat(service.loadConversation(7L, "3").latestGpaGoal()).isNull();
    }

    @Test
    void versionTwoExamAndPrerequisitePayloadRoundTripsAndRemainsAvailableWithoutCitations() {
        var payload = extendedPayload();
        var stored = message(11L, codec.encode(payload));
        when(sessions.findByIdAndUserIdAndDeletedFalse(3L, 7L)).thenReturn(Optional.of(session));
        when(messages.findBySessionIdAndSessionUserIdAndSessionDeletedFalseAndDeletedFalseOrderByIdAsc(3L, 7L))
                .thenReturn(List.of(stored));
        when(messages.findBySessionIdAndSessionUserIdAndSessionDeletedFalseAndDeletedFalseOrderByIdDesc(
                        eq(3L), eq(7L), any()))
                .thenReturn(List.of(stored));
        var detail = service.getSessionById(principal, "3");
        var context = service.loadConversation(7L, "3");
        assertThat(detail.messages().getFirst().payload()).isEqualTo(payload);
        assertThat(context.messages()).containsExactly(new ConversationMessage("ASSISTANT", payload.text()));
        assertThat(context.latestExamScore().componentScore2()).isEqualByComparingTo("7");
        assertThat(context.latestPrerequisiteImpact().courseReferences()).containsExactly("S1");
    }

    @Test
    void eachAcademicContextCanBeRecoveredOutsideTheWindowIndependently() {
        when(sessions.findByIdAndUserIdAndDeletedFalse(3L, 7L)).thenReturn(Optional.of(session));
        when(messages.findBySessionIdAndSessionUserIdAndSessionDeletedFalseAndDeletedFalseOrderByIdDesc(
                        eq(3L), eq(7L), any()))
                .thenReturn(List.of(message(99L, "Tin mới không chứa kết quả học vụ.")));
        var previous = message(11L, codec.encode(extendedPayload()));
        when(messages.findAcademicContextMessages(eq(3L), eq(7L), eq("%\"prerequisiteImpact\":{%"), any()))
                .thenReturn(List.of(previous));
        when(messages.findAcademicContextMessages(eq(3L), eq(7L), eq("%\"examScore\":{%"), any()))
                .thenReturn(List.of(previous));
        var context = service.loadConversation(7L, "3");
        assertThat(context.latestGpaGoal()).isNull();
        assertThat(context.latestExamScore().subjectName()).isEqualTo("Giải tích 1");
        assertThat(context.latestPrerequisiteImpact().scope()).isEqualTo(PrerequisiteAnalysisScope.COURSE_IMPACT);
        assertThat(context.lastMessageId()).isEqualTo(99L);
    }

    private AdvisorMessagePayloadResponse extendedPayload() {
        var prerequisiteImpact = PrerequisiteImpactResponse.builder()
                .status(PrerequisiteAnalysisStatus.COMPLETED)
                .scope(PrerequisiteAnalysisScope.COURSE_IMPACT)
                .semester(SemesterResponse.builder()
                        .id(2L)
                        .semesterName("Kỳ hiện tại")
                        .build())
                .courseReferences(List.of("S1"))
                .pendingCourseReferences(List.of())
                .courses(List.of())
                .calculatedAt(LocalDateTime.now(clock))
                .message("Phân tích độc lập.")
                .build();
        var exam = MinimumExamScoreResponse.builder()
                .status(MinimumExamScoreStatus.SOLVED)
                .subjectName("Giải tích 1")
                .componentScore1(new BigDecimal("8"))
                .componentScore2(new BigDecimal("7"))
                .targetGradeLetter("A")
                .minimumExamScore(new BigDecimal("8.943"))
                .calculatedAt(LocalDateTime.now(clock))
                .message("Tính thử từ đầu vào.")
                .build();
        return new AdvisorMessagePayloadResponse(
                2,
                AdvisorMessagePayloadResponse.TYPE,
                "Hai kết quả đã kiểm tra.",
                false,
                null,
                null,
                prerequisiteImpact,
                exam);
    }
}
