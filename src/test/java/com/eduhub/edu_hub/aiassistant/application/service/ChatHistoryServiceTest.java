package com.eduhub.edu_hub.aiassistant.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.academic.api.dto.request.GpaGoalRequest.CourseGradeConditionRequest;
import com.eduhub.edu_hub.academic.api.dto.response.*;
import com.eduhub.edu_hub.academic.domain.enums.*;
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
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.scheduling.api.dto.response.SemesterResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
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
        when(messages.findGpaContextMessages(eq(3L), eq(7L), anyString(), any()))
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
}
