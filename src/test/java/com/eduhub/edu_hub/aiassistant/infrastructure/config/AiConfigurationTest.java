package com.eduhub.edu_hub.aiassistant.infrastructure.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

class AiConfigurationTest {
    @Test
    void clientFactoryDoesNotRequirePromptOrCallProvider() {
        ChatModel model = mock(ChatModel.class);
        ChatClient client = new AiConfiguration().aiAssistantChatClient(ChatClient.builder(model));
        assertThat(client).isNotNull();
        verify(model, never()).call(any(Prompt.class));
    }

    @Test
    void applicationYamlBindsValidRagSettingsWithoutStartingApplicationContext() throws Exception {
        var environment = new MockEnvironment();
        for (var source :
                new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yaml"))) {
            environment.getPropertySources().addLast(source);
        }
        var properties = Binder.get(environment)
                .bind("eduhub.ai.rag", AiRagProperties.class)
                .orElseThrow(
                        () -> new IllegalStateException("Không bind được cấu hình eduhub.ai.rag từ application.yaml"));
        assertThat(properties.requireSystemPrompt()).contains("Chỉ trả lời quy chế", "[K123]");
        assertThat(properties.getFallbackMessage()).isNotBlank();
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(properties)).isEmpty();
        }
    }
}
