package com.eduhub.edu_hub.aiassistant.infrastructure.adapter;

import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.aiassistant.application.port.AiAssistantPort;
import com.eduhub.edu_hub.aiassistant.application.port.KnowledgeChunkPort.RetrievedChunk;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiRagProperties;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.LocalDate;
import java.util.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class SpringAiAssistantAdapter implements AiAssistantPort {
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final AiRagProperties properties;
    private final AiTokenCounter tokenCounter;

    public SpringAiAssistantAdapter(
            @Qualifier("aiAssistantChatClient") ChatClient chatClient,
            ObjectMapper objectMapper,
            AiRagProperties properties,
            AiTokenCounter tokenCounter) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.tokenCounter = tokenCounter;
    }

    @Override
    public Answer answer(String question, List<ConversationMessage> history, List<RetrievedChunk> chunks) {
        String systemPrompt = properties.requireSystemPrompt();
        List<ContextPassage> passages = chunks.stream()
                .map(chunk -> new ContextPassage(
                        "K" + chunk.chunkId(),
                        chunk.sourceTitle(),
                        chunk.pageNumber(),
                        chunk.effectiveDate(),
                        chunk.content()))
                .toList();
        String input = inputJson(Map.of("question", question, "history", history, "context", passages));
        validatePromptBudget(systemPrompt, input);
        return chatClient
                .prompt()
                .system(systemPrompt)
                .messages(new UserMessage(input))
                .options(ChatOptions.builder().temperature(0.0).maxTokens(2048))
                .call()
                .entity(Answer.class);
    }

    private String inputJson(Map<String, Object> input) {
        return "<UNTRUSTED_DATA_JSON>\n" + objectMapper.writeValueAsString(input) + "\n</UNTRUSTED_DATA_JSON>";
    }

    private void validatePromptBudget(String systemPrompt, String input) {
        // Chừa phần hướng dẫn định dạng JSON mà entity converter bổ sung; đây là bộ đếm xấp xỉ của provider.
        if (tokenCounter.countTokens(systemPrompt + input) + 512 > properties.getPromptTokenLimit()) {
            throw new AiAssistantException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    private record ContextPassage(
            String referenceCode, String sourceTitle, Integer pageNumber, LocalDate effectiveDate, String content) {}
}
