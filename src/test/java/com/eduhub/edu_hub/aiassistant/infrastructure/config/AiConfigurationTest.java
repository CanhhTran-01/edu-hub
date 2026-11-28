package com.eduhub.edu_hub.aiassistant.infrastructure.config;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantConfigurationException;
import jakarta.validation.Validation;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

class AiConfigurationTest {
    @Test
    void resolvesGeminiKeyFromEnvironmentAndUsesTheSameClientForEmbedding() {
        var environment = new MockEnvironment()
                .withProperty("GOOGLE_API_KEY", "synthetic-test-key")
                .withProperty("spring.ai.google.genai.api-key", "${GOOGLE_API_KEY:}");
        var configuration = new AiConfiguration();

        try (var client = configuration.googleGenAiClient(environment, new AiRagProperties())) {
            assertThat(client.apiKey()).isEqualTo("synthetic-test-key");
            assertThat(configuration.googleGenAiEmbeddingConnectionDetails(client).getGenAiClient())
                    .isSameAs(client);
        }
    }

    @Test
    void rejectsMissingGeminiKeyBeforeCreatingClient() {
        var environment = new MockEnvironment()
                .withProperty("spring.ai.google.genai.api-key", "${GOOGLE_API_KEY:}");

        assertThatThrownBy(() -> new AiConfiguration().googleGenAiClient(environment, new AiRagProperties()))
                .isInstanceOf(AiAssistantConfigurationException.class)
                .hasMessageContaining("GOOGLE_API_KEY");
    }

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
    void applicationYamlBindsValidAiSettingsWithoutStartingApplicationContext() throws Exception {
        var environment = new MockEnvironment();
        for (var source :
                new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yaml"))) {
            environment.getPropertySources().addLast(source);
        }
        var properties = Binder.get(environment)
                .bind("eduhub.ai.rag", AiRagProperties.class)
                .orElseThrow(
                        () -> new IllegalStateException("Không bind được cấu hình eduhub.ai.rag từ application.yaml"));
        assertThat(properties.getFallbackMessage()).isNotBlank();
        var advisor = Binder.get(environment)
                .bind("eduhub.ai.advisor", AiAdvisorProperties.class)
                .orElseThrow(() -> new IllegalStateException("Không bind được cấu hình eduhub.ai.advisor"));
        assertThat(advisor.requireSystemPrompt()).isNotBlank();
        assertThat(advisor.requireSimulationCommentPrompt()).contains("What-If Simulator");
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(properties)).isEmpty();
            assertThat(validatorFactory.getValidator().validate(advisor)).isEmpty();
        }
    }
}
