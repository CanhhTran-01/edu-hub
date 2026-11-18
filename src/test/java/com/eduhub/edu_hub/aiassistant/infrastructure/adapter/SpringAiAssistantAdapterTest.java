package com.eduhub.edu_hub.aiassistant.infrastructure.adapter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantConfigurationException;
import com.eduhub.edu_hub.aiassistant.application.port.KnowledgeChunkPort.RetrievedChunk;
import com.eduhub.edu_hub.aiassistant.domain.enums.AiIntent;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiRagProperties;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.*;
import tools.jackson.databind.json.JsonMapper;

class SpringAiAssistantAdapterTest {
    @Test
    void sendsReferenceIdsDatesAndDocumentPositionsAndParsesStructuredAnswer() {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        when(model.call(any(Prompt.class)))
                .thenReturn(
                        new ChatResponse(
                                List.of(
                                        new Generation(
                                                new AssistantMessage(
                                                        """
                        {"content":"Theo quy chế [K21].","citationIds":[21]}
                        """)))));
        var properties = new AiRagProperties();
        properties.setSystemPrompt("Chỉ trả lời từ ngữ cảnh và dẫn mã nguồn.");
        var adapter = new SpringAiAssistantAdapter(
                ChatClient.create(model),
                JsonMapper.builder().findAndAddModules().build(),
                properties,
                new AiTokenCounter());
        var chunk = new RetrievedChunk(
                21L, 3L, "Quy chế", "Điều 1", 2, "Nội dung quy chế.", 10, 0.9, LocalDate.of(2026, 1, 1));
        var answer = adapter.answer("Quy chế?", List.of(), List.of(chunk));
        assertThat(answer.content()).isEqualTo("Theo quy chế [K21].");
        assertThat(answer.citationIds()).containsExactly(21L);
        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertThat(prompt.getValue().getSystemMessage().getText()).isEqualTo(properties.getSystemPrompt());
        assertThat(prompt.getValue().getUserMessage().getText())
                .contains("K21", "2026-01-01", "Điều 1", "UNTRUSTED_DATA_JSON");
    }

    @Test
    void parsesScopeAndStandaloneQuestionWithoutAnsweringFromHistory() {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        when(model.call(any(Prompt.class)))
                .thenReturn(
                        new ChatResponse(
                                List.of(
                                        new Generation(
                                                new AssistantMessage(
                                                        """
                        {"intent":"KNOWLEDGE","standaloneQuestion":"Quy chế học lại?"}
                        """)))));
        var adapter = new SpringAiAssistantAdapter(
                ChatClient.create(model),
                JsonMapper.builder().findAndAddModules().build(),
                new AiRagProperties(),
                new AiTokenCounter());
        var analysis = adapter.analyzeQuestion("Học lại?", List.of());
        assertThat(analysis.intent()).isEqualTo(AiIntent.KNOWLEDGE);
        assertThat(analysis.standaloneQuestion()).isEqualTo("Quy chế học lại?");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void missingAnswerPromptReportsItsConfigurationKeyBeforeCallingModel(String systemPrompt) {
        ChatModel model = mock(ChatModel.class);
        var properties = new AiRagProperties();
        properties.setSystemPrompt(systemPrompt);
        var adapter = new SpringAiAssistantAdapter(
                ChatClient.create(model),
                JsonMapper.builder().findAndAddModules().build(),
                properties,
                new AiTokenCounter());
        assertThatThrownBy(() -> adapter.answer("Quy chế?", List.of(), List.of()))
                .isInstanceOf(AiAssistantConfigurationException.class)
                .hasMessageContaining("eduhub.ai.rag.system-prompt");
        verify(model, never()).call(any(Prompt.class));
    }
}
