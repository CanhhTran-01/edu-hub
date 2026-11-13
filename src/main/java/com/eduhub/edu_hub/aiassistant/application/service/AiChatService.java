package com.eduhub.edu_hub.aiassistant.application.service;

import com.eduhub.edu_hub.aiassistant.api.dto.request.ChatRequest;
import com.eduhub.edu_hub.aiassistant.api.dto.response.ChatResponse;
import com.eduhub.edu_hub.aiassistant.api.dto.response.CitationResponse;
import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.aiassistant.application.port.AiAssistantPort;
import com.eduhub.edu_hub.aiassistant.domain.enums.ChatMessageRole;
import com.eduhub.edu_hub.aiassistant.domain.model.ChatCitation;
import com.eduhub.edu_hub.aiassistant.domain.model.ChatMessage;
import com.eduhub.edu_hub.aiassistant.domain.model.ChatSession;
import com.eduhub.edu_hub.aiassistant.domain.repository.ChatCitationRepository;
import com.eduhub.edu_hub.aiassistant.domain.repository.ChatMessageRepository;
import com.eduhub.edu_hub.aiassistant.domain.repository.ChatSessionRepository;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiRagProperties;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.shared.enums.AiIntent;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiChatService {
    private final AiAccessService accessService;
    private final AiAssistantPort assistantPort;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatCitationRepository citationRepository;
    private final AiRagProperties properties;

    @Transactional
    public ChatResponse chat(Jwt jwt, ChatRequest request) {
        UserInfoResponse user = accessService.requireStudent(jwt);
        ChatSession session = resolveSession(request.getSessionId(), user.getId());

        AiAssistantPort.Answer answer =
                assistantPort.answer(request.getMessage(), session.getId().toString());
        boolean hasContext = !answer.citations().isEmpty();
        AiIntent intent = hasContext ? AiIntent.KNOWLEDGE : AiIntent.OUT_OF_SCOPE;
        String content = hasContext ? answer.content() : properties.getFallbackMessage();
        List<AiAssistantPort.Citation> citations = hasContext ? answer.citations() : List.of();

        messageRepository.save(new ChatMessage(session, ChatMessageRole.USER, request.getMessage(), null));
        ChatMessage assistantMessage =
                messageRepository.save(new ChatMessage(session, ChatMessageRole.ASSISTANT, content, intent));
        if (!citations.isEmpty()) {
            citationRepository.saveAll(citations.stream()
                    .map(citation -> new ChatCitation(
                            assistantMessage,
                            citation.sourceTitle(),
                            citation.sectionTitle(),
                            citation.pageNumber(),
                            citation.snippet()))
                    .toList());
        }

        return ChatResponse.builder()
                .sessionId(session.getId().toString())
                .messageId(assistantMessage.getId().toString())
                .content(content)
                .intent(intent)
                .citations(citations.stream().map(this::response).toList())
                .scenario(null)
                .build();
    }

    private ChatSession resolveSession(String requestedSessionId, Long userId) {
        if (requestedSessionId == null || requestedSessionId.isBlank()) {
            return sessionRepository.save(new ChatSession(userId));
        }
        try {
            return sessionRepository
                    .findByIdAndUserIdAndDeletedFalse(Long.valueOf(requestedSessionId), userId)
                    .orElseThrow(() -> new AiAssistantException(ErrorCode.CHAT_SESSION_NOT_FOUND));
        } catch (NumberFormatException exception) {
            throw new AiAssistantException(ErrorCode.CHAT_SESSION_NOT_FOUND);
        }
    }

    private CitationResponse response(AiAssistantPort.Citation citation) {
        return CitationResponse.builder()
                .sourceTitle(citation.sourceTitle())
                .sectionTitle(citation.sectionTitle())
                .pageNumber(citation.pageNumber())
                .snippet(citation.snippet())
                .build();
    }
}
