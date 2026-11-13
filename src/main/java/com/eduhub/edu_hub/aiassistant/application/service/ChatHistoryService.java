package com.eduhub.edu_hub.aiassistant.application.service;

import com.eduhub.edu_hub.aiassistant.api.dto.response.ChatHistoryMessageResponse;
import com.eduhub.edu_hub.aiassistant.api.dto.response.ChatSessionDetailResponse;
import com.eduhub.edu_hub.aiassistant.api.dto.response.ChatSessionSummaryResponse;
import com.eduhub.edu_hub.aiassistant.api.dto.response.CitationResponse;
import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.aiassistant.domain.model.ChatCitation;
import com.eduhub.edu_hub.aiassistant.domain.model.ChatMessage;
import com.eduhub.edu_hub.aiassistant.domain.model.ChatSession;
import com.eduhub.edu_hub.aiassistant.domain.repository.ChatCitationRepository;
import com.eduhub.edu_hub.aiassistant.domain.repository.ChatMessageRepository;
import com.eduhub.edu_hub.aiassistant.domain.repository.ChatSessionRepository;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatHistoryService {
    private final AiAccessService accessService;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatCitationRepository citationRepository;

    public PageResponse<ChatSessionSummaryResponse> sessions(Jwt jwt, int page, int size) {
        UserInfoResponse user = accessService.requireStudent(jwt);
        Page<ChatSession> sessions =
                sessionRepository.findByUserIdAndDeletedFalseOrderByIdDesc(user.getId(), PageRequest.of(page, size));
        return PageResponse.<ChatSessionSummaryResponse>builder()
                .content(sessions.getContent().stream().map(this::summary).toList())
                .pageNumber(sessions.getNumber())
                .pageSize(sessions.getSize())
                .totalElements(sessions.getTotalElements())
                .totalPages(sessions.getTotalPages())
                .isLast(sessions.isLast())
                .build();
    }

    public ChatSessionDetailResponse session(Jwt jwt, String sessionId) {
        UserInfoResponse user = accessService.requireStudent(jwt);
        ChatSession session = ownedSession(sessionId, user.getId());
        List<ChatMessage> messages = messageRepository.findBySessionIdAndDeletedFalseOrderByIdAsc(session.getId());
        List<Long> messageIds = messages.stream().map(ChatMessage::getId).toList();
        Map<Long, List<ChatCitation>> citations = messageIds.isEmpty()
                ? Map.of()
                : citationRepository.findByMessageIdInAndDeletedFalseOrderByIdAsc(messageIds).stream()
                        .collect(Collectors.groupingBy(
                                citation -> citation.getMessage().getId()));
        return new ChatSessionDetailResponse(
                session.getId().toString(),
                session.getCreatedAt(),
                messages.stream().map(message -> response(message, citations)).toList());
    }

    private ChatSessionSummaryResponse summary(ChatSession session) {
        ChatMessage lastMessage =
                messageRepository
                        .findBySessionIdAndDeletedFalseOrderByIdDesc(session.getId(), PageRequest.of(0, 1))
                        .stream()
                        .findFirst()
                        .orElse(null);
        return new ChatSessionSummaryResponse(
                session.getId().toString(),
                session.getCreatedAt(),
                lastMessage == null ? session.getCreatedAt() : lastMessage.getCreatedAt(),
                lastMessage == null ? null : lastMessage.getContent());
    }

    private ChatHistoryMessageResponse response(ChatMessage message, Map<Long, List<ChatCitation>> citations) {
        List<CitationResponse> messageCitations = citations.getOrDefault(message.getId(), List.of()).stream()
                .map(this::response)
                .toList();
        return new ChatHistoryMessageResponse(
                message.getId().toString(),
                message.getRole(),
                message.getContent(),
                message.getIntent(),
                message.getCreatedAt(),
                messageCitations);
    }

    private CitationResponse response(ChatCitation citation) {
        return CitationResponse.builder()
                .sourceTitle(citation.getSourceTitle())
                .sectionTitle(citation.getSectionTitle())
                .pageNumber(citation.getPageNumber())
                .snippet(citation.getSnippet())
                .build();
    }

    private ChatSession ownedSession(String sessionId, Long userId) {
        try {
            return sessionRepository
                    .findByIdAndUserIdAndDeletedFalse(Long.valueOf(sessionId), userId)
                    .orElseThrow(() -> new AiAssistantException(ErrorCode.CHAT_SESSION_NOT_FOUND));
        } catch (NumberFormatException exception) {
            throw new AiAssistantException(ErrorCode.CHAT_SESSION_NOT_FOUND);
        }
    }
}
