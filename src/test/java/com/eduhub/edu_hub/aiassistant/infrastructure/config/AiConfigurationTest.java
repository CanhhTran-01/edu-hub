package com.eduhub.edu_hub.aiassistant.infrastructure.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantConfigurationException;
import jakarta.validation.Validation;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

class AiConfigurationTest {
    @Test
    void advisorChecksTrackedRequestCapacityWithoutRelyingOnBeanValidation() {
        var properties = new AiAdvisorProperties();
        properties.setMaximumTrackedRequests(8);
        properties.setMaximumConcurrentRequests(8);
        assertThat(properties.isResourceConfigurationValid()).isTrue();

        properties.setMaximumTrackedRequests(7);
        assertThat(properties.isResourceConfigurationValid()).isFalse();

        properties.setMaximumTrackedRequests(8);
        properties.setMaximumConcurrentRequests(9);
        assertThat(properties.isResourceConfigurationValid()).isFalse();
    }

    @Test
    void advisorChecksCombinedTokenReservationWithoutIntegerOverflow() {
        var properties = new AiAdvisorProperties();
        properties.setMaximumTotalTokens(properties.getMaximumContextTokens() + properties.getMaximumOutputTokens());
        assertThat(properties.isResourceConfigurationValid()).isTrue();

        properties.setMaximumTotalTokens(properties.getMaximumTotalTokens() - 1);
        assertThat(properties.isResourceConfigurationValid()).isFalse();

        properties.setMaximumContextTokens(Integer.MAX_VALUE);
        properties.setMaximumTotalTokens(Integer.MAX_VALUE);
        assertThat(properties.isResourceConfigurationValid()).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"PT0S", "PT0.999S", "PT2M0.000000001S"})
    void advisorRejectsMissingOrUnsupportedExecutionTimeout(String configuredTimeout) {
        var properties = new AiAdvisorProperties();
        properties.setExecutionTimeout(configuredTimeout == null ? null : Duration.parse(configuredTimeout));
        assertThat(properties.isResourceConfigurationValid()).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"PT0S", "PT59.999S", "PT1H0.000000001S"})
    void advisorRejectsMissingOrUnsupportedRequestRetention(String configuredRetention) {
        var properties = new AiAdvisorProperties();
        properties.setRequestRetention(configuredRetention == null ? null : Duration.parse(configuredRetention));
        assertThat(properties.isResourceConfigurationValid()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"PT1S, PT1M", "PT2M, PT1H"})
    void advisorAcceptsSupportedDurationBoundaries(String configuredTimeout, String configuredRetention) {
        var properties = new AiAdvisorProperties();
        properties.setExecutionTimeout(Duration.parse(configuredTimeout));
        properties.setRequestRetention(Duration.parse(configuredRetention));
        assertThat(properties.isResourceConfigurationValid()).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void advisorRejectsMissingSystemPromptBeforeCallingModel(String systemPrompt) {
        var properties = new AiAdvisorProperties();
        properties.setSystemPrompt(systemPrompt);
        assertThatThrownBy(properties::requireSystemPrompt)
                .isInstanceOf(AiAssistantConfigurationException.class)
                .hasMessageContaining("eduhub.ai.advisor.system-prompt");
    }

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
        var advisor = Binder.get(environment)
                .bind("eduhub.ai.advisor", AiAdvisorProperties.class)
                .orElseThrow(() -> new IllegalStateException("Không bind được cấu hình eduhub.ai.advisor"));
        assertThat(advisor.requireSystemPrompt()).contains("getAcademicProfile", "searchAcademicKnowledge");
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(properties)).isEmpty();
            assertThat(validatorFactory.getValidator().validate(advisor)).isEmpty();
        }
    }
}
