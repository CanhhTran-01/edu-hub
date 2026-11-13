package com.eduhub.edu_hub.aiassistant.infrastructure.adapter;

import com.eduhub.edu_hub.aiassistant.application.port.AiAssistantPort;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class SpringAiAssistantAdapter implements AiAssistantPort {
    private final ChatClient chatClient;

    public SpringAiAssistantAdapter(@Qualifier("aiAssistantChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Answer answer(String message, String conversationId) {
        ChatClientResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .chatClientResponse();

        List<Document> documents = retrievedDocuments(response);
        String content =
                response.chatResponse() == null || response.chatResponse().getResult() == null
                        ? ""
                        : response.chatResponse().getResult().getOutput().getText();
        return new Answer(content, documents.stream().map(this::citation).toList());
    }

    @SuppressWarnings("unchecked")
    private List<Document> retrievedDocuments(ChatClientResponse response) {
        Object value = response.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        return value instanceof List<?> list
                ? list.stream()
                        .filter(Document.class::isInstance)
                        .map(Document.class::cast)
                        .toList()
                : List.of();
    }

    private Citation citation(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        return new Citation(
                stringValue(metadata.get("source")),
                stringValue(metadata.get("sectionTitle")),
                integerValue(metadata.get("pageNumber")),
                document.getText());
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value == null) return null;
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
