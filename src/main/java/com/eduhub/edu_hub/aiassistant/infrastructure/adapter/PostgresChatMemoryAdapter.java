package com.eduhub.edu_hub.aiassistant.infrastructure.adapter;

import com.eduhub.edu_hub.aiassistant.domain.enums.ChatMessageRole;
import com.eduhub.edu_hub.aiassistant.domain.model.ChatMessage;
import com.eduhub.edu_hub.aiassistant.domain.repository.ChatMessageRepository;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiRagProperties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PostgresChatMemoryAdapter implements ChatMemory {
    private final ChatMessageRepository messageRepository;
    private final AiRagProperties properties;

    @Override
    public void add(String conversationId, List<Message> messages) {
        // Cố ý no-op: AiChatService là nơi duy nhất ghi message, advisor không được tạo bản ghi trùng.
    }

    @Override
    @Transactional(readOnly = true)
    public List<Message> get(String conversationId) {
        Long sessionId;
        try {
            sessionId = Long.valueOf(conversationId);
        } catch (NumberFormatException exception) {
            return List.of();
        }
        // Lấy N tin mới nhất bằng index (session_id, id DESC), rồi đảo lại để prompt đi từ cũ đến mới.
        List<ChatMessage> stored = new ArrayList<>(messageRepository.findBySessionIdAndDeletedFalseOrderByIdDesc(
                sessionId, PageRequest.of(0, properties.getMemoryMessageLimit())));
        Collections.reverse(stored);
        return stored.stream().map(this::toSpringMessage).toList();
    }

    @Override
    public void clear(String conversationId) {
        // Cố ý no-op: vòng đời lịch sử thuộc application service, không thuộc advisor.
    }

    private Message toSpringMessage(ChatMessage message) {
        return message.getRole() == ChatMessageRole.USER
                ? new UserMessage(message.getContent())
                : new AssistantMessage(message.getContent());
    }
}
