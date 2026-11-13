package com.eduhub.edu_hub.aiassistant.infrastructure.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiRagProperties.class)
public class AiConfiguration {
    /**
     * ChatClient dùng cho trợ lý học vụ. Memory advisor cung cấp lịch sử PostgreSQL, còn QuestionAnswerAdvisor bổ sung
     * các đoạn tài liệu liên quan theo cấu hình eduhub.ai.rag.*.
     */
    @Bean
    ChatClient aiAssistantChatClient(
            ChatClient.Builder builder, ChatMemory chatMemory, VectorStore vectorStore, AiRagProperties properties) {
        return builder.defaultSystem(properties.getSystemPrompt())
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder()
                                        .topK(properties.getTopK())
                                        .similarityThreshold(properties.getSimilarityThreshold())
                                        .build())
                                .build())
                .build();
    }
}
