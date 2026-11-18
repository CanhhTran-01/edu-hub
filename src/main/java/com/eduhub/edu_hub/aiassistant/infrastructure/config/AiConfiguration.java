package com.eduhub.edu_hub.aiassistant.infrastructure.config;

import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantConfigurationException;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.*;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(AiRagProperties.class)
public class AiConfiguration {
    private static final int MAXIMUM_CHAT_WORKERS = 8;
    private static final int MAXIMUM_QUEUED_CHAT_REQUESTS = 16;

    /**
     * Lịch sử và ngữ cảnh được application cung cấp tường minh; ChatClient chỉ gọi model.
     */
    @Bean
    ChatClient aiAssistantChatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    Client googleGenAiClient(@Value("${spring.ai.google.genai.api-key:}") String apiKey, AiRagProperties properties) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiAssistantConfigurationException("Thiếu cấu hình GOOGLE_API_KEY cho dịch vụ AI");
        }
        return Client.builder()
                .apiKey(apiKey)
                .httpOptions(HttpOptions.builder()
                        .timeout(Math.toIntExact(properties.getRequestTimeout().toMillis()))
                        .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                        .build())
                .build();
    }

    @Bean
    GoogleGenAiEmbeddingConnectionDetails googleGenAiEmbeddingConnectionDetails(Client googleGenAiClient) {
        return GoogleGenAiEmbeddingConnectionDetails.builder()
                .genAiClient(googleGenAiClient)
                .build();
    }

    @Bean
    ThreadPoolTaskExecutor aiRequestExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(MAXIMUM_CHAT_WORKERS);
        executor.setMaxPoolSize(MAXIMUM_CHAT_WORKERS);
        executor.setQueueCapacity(MAXIMUM_QUEUED_CHAT_REQUESTS);
        executor.setThreadNamePrefix("ai-request-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }

    @Bean
    ThreadPoolTaskExecutor knowledgeIngestionExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("knowledge-ingestion-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }

    // Scheduler này chỉ phục vụ worker AI, không thay scheduler mặc định của notification/scheduling.
    @Bean(defaultCandidate = false)
    ThreadPoolTaskScheduler knowledgeIngestionScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("knowledge-poll-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }
}
