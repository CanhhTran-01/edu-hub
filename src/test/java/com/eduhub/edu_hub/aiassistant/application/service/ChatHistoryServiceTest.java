package com.eduhub.edu_hub.aiassistant.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.aiassistant.application.port.AiAdvisorModelPort.ConversationMessage;
import com.eduhub.edu_hub.aiassistant.application.port.KnowledgeChunkPort;
import com.eduhub.edu_hub.aiassistant.application.port.KnowledgeChunkPort.RetrievedChunk;
import com.eduhub.edu_hub.aiassistant.domain.enums.*;
import com.eduhub.edu_hub.aiassistant.domain.model.*;
import com.eduhub.edu_hub.aiassistant.domain.repository.*;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiRagProperties;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.Jwt;

class ChatHistoryServiceTest {
    AiAccessService accessService = mock(AiAccessService.class);
    ChatSessionRepository sessions = mock(ChatSessionRepository.class);
    ChatMessageRepository messages = mock(ChatMessageRepository.class);
    MessageChunkReferenceRepository references = mock(MessageChunkReferenceRepository.class);
    KnowledgeChunkPort chunks = mock(KnowledgeChunkPort.class);
    AiRagProperties properties = new AiRagProperties();
    Clock clock = Clock.fixed(Instant.parse("2026-11-18T17:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    ChatHistoryService service =
            new ChatHistoryService(accessService, sessions, messages, references, chunks, properties, clock);
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
}
