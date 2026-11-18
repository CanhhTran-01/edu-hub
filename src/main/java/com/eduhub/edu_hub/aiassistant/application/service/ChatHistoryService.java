package com.eduhub.edu_hub.aiassistant.application.service;

import com.eduhub.edu_hub.aiassistant.api.dto.request.SearchChatSessionsRequest;
import com.eduhub.edu_hub.aiassistant.api.dto.response.*;
import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.aiassistant.application.port.AiAssistantPort.ConversationMessage;
import com.eduhub.edu_hub.aiassistant.application.port.KnowledgeChunkPort;
import com.eduhub.edu_hub.aiassistant.application.port.KnowledgeChunkPort.RetrievedChunk;
import com.eduhub.edu_hub.aiassistant.application.specification.ChatSessionSpecification;
import com.eduhub.edu_hub.aiassistant.domain.enums.*;
import com.eduhub.edu_hub.aiassistant.domain.model.*;
import com.eduhub.edu_hub.aiassistant.domain.repository.*;
import com.eduhub.edu_hub.aiassistant.domain.repository.MessageChunkReferenceRepository.ReferenceProjection;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiRagProperties;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ChatHistoryService {
    private final AiAccessService accessService;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final MessageChunkReferenceRepository referenceRepository;
    private final KnowledgeChunkPort chunkPort;
    private final AiRagProperties properties;
    private final Clock clock;

    public PageResponse<ChatSessionSummaryResponse> getAllSessions(Jwt principal, SearchChatSessionsRequest request) {
        Long userId = accessService.requireStudent(principal).getId();
        if (request.getPage() < 0
                || request.getSize() < 1
                || request.getSize() > 100
                || (request.getFromDate() != null
                        && request.getToDate() != null
                        && request.getFromDate().isAfter(request.getToDate()))
                || (request.getToDate() != null && request.getToDate().getYear() >= 9999)) {
            throw new AiAssistantException(ErrorCode.VALIDATION_ERROR);
        }
        var sessions = sessionRepository.findAll(
                ChatSessionSpecification.ownedSessions(
                        userId, request.getKeyword(), request.getFromDate(), request.getToDate()),
                PageRequest.of(request.getPage(), request.getSize(), Sort.by(Sort.Direction.DESC, "startedAt", "id")));
        List<Long> sessionIds =
                sessions.getContent().stream().map(ChatSession::getId).toList();
        Map<Long, String> lastMessages = sessionIds.isEmpty()
                ? Map.of()
                : messageRepository.findLastMessages(userId, sessionIds).stream()
                        .collect(Collectors.toMap(
                                ChatMessageRepository.LastMessageProjection::getSessionId,
                                ChatMessageRepository.LastMessageProjection::getContent));
        return PageResponse.<ChatSessionSummaryResponse>builder()
                .content(sessions.getContent().stream()
                        .map(session -> new ChatSessionSummaryResponse(
                                session.getId().toString(),
                                session.getTitle(),
                                session.getTopicType(),
                                session.getStartedAt(),
                                session.getLastMessageAt(),
                                lastMessages.get(session.getId())))
                        .toList())
                .pageNumber(sessions.getNumber())
                .pageSize(sessions.getSize())
                .totalElements(sessions.getTotalElements())
                .totalPages(sessions.getTotalPages())
                .isLast(sessions.isLast())
                .build();
    }

    public ChatSessionDetailResponse getSessionById(Jwt principal, String sessionId) {
        Long userId = accessService.requireStudent(principal).getId();
        ChatSession session = ownedSession(sessionId, userId);
        // TODO(CHAT_HISTORY_PAGINATION): Bổ sung phân trang cho phiên dài; hiện trả toàn bộ chi tiết phiên.
        List<ChatMessage> messages =
                messageRepository.findBySessionIdAndSessionUserIdAndSessionDeletedFalseAndDeletedFalseOrderByIdAsc(
                        session.getId(), userId);
        var references = loadReferences(userId, messages);
        return new ChatSessionDetailResponse(
                session.getId().toString(),
                session.getTitle(),
                session.getTopicType(),
                session.getStartedAt(),
                messages.stream()
                        .map(message -> new ChatHistoryMessageResponse(
                                message.getId().toString(),
                                message.getRole(),
                                message.getContent(),
                                message.getIntent(),
                                message.getSentAt(),
                                references.getOrDefault(message.getId(), List.of()).stream()
                                        .map(this::citationResponse)
                                        .toList()))
                        .toList());
    }

    public ConversationContext loadConversation(Long userId, String requestedSessionId) {
        if (requestedSessionId == null || requestedSessionId.isBlank()) {
            return new ConversationContext(null, null, List.of());
        }
        ChatSession session = ownedSession(requestedSessionId, userId);
        var newestMessages =
                messageRepository.findBySessionIdAndSessionUserIdAndSessionDeletedFalseAndDeletedFalseOrderByIdDesc(
                        session.getId(), userId, PageRequest.of(0, properties.getMemoryMessageLimit()));
        Long lastMessageId =
                newestMessages.isEmpty() ? null : newestMessages.getFirst().getId();
        var references = loadReferences(userId, newestMessages);
        List<ChatMessage> messages = new ArrayList<>(newestMessages);
        Collections.reverse(messages);
        List<ConversationMessage> conversation = new ArrayList<>();
        for (ChatMessage message : messages) {
            var messageReferences = references.getOrDefault(message.getId(), List.of());
            if (message.getRole() == ChatMessageRole.ASSISTANT
                    && message.getIntent() == AiIntent.KNOWLEDGE
                    && (messageReferences.isEmpty()
                            || messageReferences.stream()
                                    .anyMatch(reference -> !Boolean.TRUE.equals(reference.getSourceAvailable()))))
                continue;
            conversation.add(new ConversationMessage(message.getRole().name(), message.getContent()));
        }
        return new ConversationContext(session.getId(), lastMessageId, List.copyOf(conversation));
    }

    @Transactional
    public ChatResponse recordExchange(
            Jwt principal,
            ConversationContext context,
            String question,
            AiIntent intent,
            String proposedContent,
            List<RetrievedChunk> citedChunks) {
        Long userId = accessService.requireStudent(principal).getId();
        var now = LocalDateTime.now(clock);
        ChatSession session;
        if (context.sessionId() == null) {
            int titleEnd = question.offsetByCodePoints(0, Math.min(120, question.codePointCount(0, question.length())));
            session = sessionRepository.save(new ChatSession(userId, question.substring(0, titleEnd), now));
        } else {
            session = sessionRepository
                    .findOwnedForUpdate(context.sessionId(), userId)
                    .orElseThrow(() -> deniedSession(userId));
            var currentMessages =
                    messageRepository.findBySessionIdAndSessionUserIdAndSessionDeletedFalseAndDeletedFalseOrderByIdDesc(
                            session.getId(), userId, PageRequest.of(0, 1));
            Long currentLastMessageId = currentMessages.isEmpty()
                    ? null
                    : currentMessages.getFirst().getId();
            if (!Objects.equals(currentLastMessageId, context.lastMessageId())) {
                throw new AiAssistantException(ErrorCode.CHAT_SESSION_CHANGED);
            }
        }

        String content = proposedContent;
        List<RetrievedChunk> referencesToSave = citedChunks;
        List<Long> citedChunkIds =
                citedChunks.stream().map(RetrievedChunk::chunkId).toList();
        if (!chunkPort
                .lockAvailableChunkIds(citedChunkIds, LocalDate.now(clock))
                .containsAll(citedChunkIds)) {
            content = properties.getFallbackMessage();
            referencesToSave = List.of();
        }
        // Chỉ service này ghi lịch sử. Cả cặp message và tham chiếu cùng commit; gọi AI đã hoàn tất bên ngoài
        // transaction.
        messageRepository.save(new ChatMessage(session, ChatMessageRole.USER, question, intent, now));
        ChatMessage assistantMessage =
                messageRepository.save(new ChatMessage(session, ChatMessageRole.ASSISTANT, content, intent, now));
        if (!referencesToSave.isEmpty()) {
            referenceRepository.saveAll(referencesToSave.stream()
                    .map(chunk -> new MessageChunkReference(assistantMessage, chunk.chunkId(), chunk.relevanceScore()))
                    .toList());
        }
        session.recordMessageAt(now);
        return ChatResponse.builder()
                .sessionId(session.getId().toString())
                .messageId(assistantMessage.getId().toString())
                .content(content)
                .intent(intent)
                .citations(referencesToSave.stream()
                        .map(chunk -> CitationResponse.builder()
                                .referenceCode("K" + chunk.chunkId())
                                .chunkId(chunk.chunkId())
                                .sourceId(chunk.sourceId())
                                .sourceTitle(chunk.sourceTitle())
                                .sectionTitle(chunk.sectionTitle())
                                .pageNumber(chunk.pageNumber())
                                .snippet(chunk.content())
                                .sourceAvailable(true)
                                .build())
                        .toList())
                .build();
    }

    private Map<Long, List<ReferenceProjection>> loadReferences(Long userId, List<ChatMessage> messages) {
        List<Long> messageIds = messages.stream().map(ChatMessage::getId).toList();
        // Đọc citation theo lô rồi nhóm theo message, tránh một truy vấn riêng cho từng câu trả lời.
        return messageIds.isEmpty()
                ? Map.of()
                : referenceRepository.findOwnedReferences(userId, messageIds, LocalDate.now(clock)).stream()
                        .collect(Collectors.groupingBy(ReferenceProjection::getMessageId));
    }

    private CitationResponse citationResponse(ReferenceProjection reference) {
        boolean available = Boolean.TRUE.equals(reference.getSourceAvailable());
        return CitationResponse.builder()
                .referenceCode(reference.getChunkId() == null ? null : "K" + reference.getChunkId())
                .chunkId(reference.getChunkId())
                .sourceId(reference.getSourceId())
                .sourceTitle(reference.getSourceTitle())
                .sectionTitle(reference.getSectionTitle())
                .pageNumber(reference.getPageNumber())
                .snippet(available ? reference.getSnippet() : null)
                .sourceAvailable(available)
                .build();
    }

    private ChatSession ownedSession(String sessionId, Long userId) {
        try {
            // Luôn lọc theo cả sessionId và userId, tránh lỗi IDOR
            // Không dùng findById đơn lẻ để không làm lộ session có tồn tại nhưng thuộc sinh viên khác.
            return sessionRepository
                    .findByIdAndUserIdAndDeletedFalse(Long.valueOf(sessionId), userId)
                    .orElseThrow(() -> deniedSession(userId));
        } catch (NumberFormatException exception) {
            throw deniedSession(userId);
        }
    }

    private AiAssistantException deniedSession(Long userId) {
        log.warn("Từ chối đọc phiên hội thoại không khả dụng: actorId={}", userId);
        return new AiAssistantException(ErrorCode.CHAT_SESSION_NOT_FOUND);
    }

    public record ConversationContext(Long sessionId, Long lastMessageId, List<ConversationMessage> messages) {}
}
