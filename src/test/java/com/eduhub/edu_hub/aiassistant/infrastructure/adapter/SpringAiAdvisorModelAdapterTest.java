package com.eduhub.edu_hub.aiassistant.infrastructure.adapter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.aiassistant.api.dto.response.AdvisorEventResponse;
import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.aiassistant.application.service.*;
import com.eduhub.edu_hub.aiassistant.application.service.AdvisorChatExecution.AdvisorExecutionStoppedException;
import com.eduhub.edu_hub.aiassistant.domain.enums.AdvisorRequestState;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiAdvisorProperties;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.json.JsonMapper;

class SpringAiAdvisorModelAdapterTest {
    ChatModel model = mock(ChatModel.class);
    AdvisorToolService tools = mock(AdvisorToolService.class);
    AiAdvisorProperties properties = new AiAdvisorProperties();
    AiTokenCounter counter = mock(AiTokenCounter.class);
    Clock clock = Clock.fixed(Instant.parse("2026-11-25T17:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    AdvisorChatExecution execution;
    SpringAiAdvisorModelAdapter adapter;
    List<String> eventNames = new ArrayList<>();
    List<AdvisorEventResponse> events = new ArrayList<>();

    @BeforeEach
    void prepare() {
        properties.setSystemPrompt("Chỉ dùng dữ liệu công cụ và trả JSON content/citationIds.");
        execution = new AdvisorChatExecution(
                UUID.randomUUID(),
                7L,
                Jwt.withTokenValue("test")
                        .header("alg", "HS256")
                        .subject("student")
                        .build(),
                "GPA?",
                new ChatHistoryService.ConversationContext(null, null, List.of()),
                properties,
                counter,
                clock);
        execution.connect(
                (name, event) -> {
                    eventNames.add(name);
                    events.add(event);
                },
                () -> {});
        adapter = new SpringAiAdvisorModelAdapter(
                model, tools, JsonMapper.builder().findAndAddModules().build(), properties);
    }

    @Test
    void missingModelResponseBecomesAnAiServiceErrorWithoutExecutingTools() {
        when(model.call(any(Prompt.class))).thenReturn((ChatResponse) null);

        assertThatThrownBy(() -> adapter.answer(execution, List.of()))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE));
        verifyNoInteractions(tools);
        assertThat(eventNames).isEmpty();
        assertThat(execution.getStatus().modelCalls()).isEqualTo(1);
        assertThat(execution.getStatus().toolCalls()).isZero();
    }

    @Test
    void missingKnowledgeQueryIsRejectedAfterFrameworkNormalizesNullArguments() {
        // Spring AI chuẩn hóa arguments null thành {}; tool tra cứu vẫn phải từ chối vì thiếu query.
        when(model.call(any(Prompt.class)))
                .thenReturn(responseWithTool("k1", "searchAcademicKnowledge", null), finalResponse());

        assertThat(adapter.answer(execution, List.of()).content()).isEqualTo("Đã tra cứu.");

        verifyNoInteractions(tools);
        assertThat(eventNames).isEmpty();
        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        var toolResponses = prompts.getAllValues().getLast().getInstructions().stream()
                .filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast)
                .flatMap(message -> message.getResponses().stream())
                .toList();
        assertThat(toolResponses).hasSize(1);
        assertThat(toolResponses.getFirst().responseData()).contains("\"code\":1001");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void missingToolCallIdUsesTheSameGeneratedTraceIdForStartedAndCompletedEvents(String providerToolCallId) {
        when(model.call(any(Prompt.class)))
                .thenReturn(responseWithTool(providerToolCallId, "getAcademicProfile", "{}"), finalResponse());

        assertThat(adapter.answer(execution, List.of()).content()).isEqualTo("Đã tra cứu.");

        verify(tools).getAcademicProfile(execution);
        assertThat(eventNames).containsExactly("tool.started", "tool.completed");
        String startedToolCallId = (String) ((Map<?, ?>) events.getFirst().data()).get("toolCallId");
        String completedToolCallId = (String) ((Map<?, ?>) events.getLast().data()).get("toolCallId");
        assertThatCode(() -> UUID.fromString(startedToolCallId)).doesNotThrowAnyException();
        assertThat(completedToolCallId).isEqualTo(startedToolCallId);
    }

    @Test
    void frameworkKeepsToolIdsAndProviderSignaturesAcrossMultipleCalls() {
        var signatures = List.of(new byte[] {1, 2});
        var profileCall = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("p1", "function", "getAcademicProfile", "{}")))
                .properties(Map.of("thoughtSignatures", signatures))
                .build();
        when(model.call(any(Prompt.class)))
                .thenReturn(
                        new ChatResponse(List.of(new Generation(profileCall))),
                        responseWithTool("k1", "searchAcademicKnowledge", "{\"query\":\"Quy chế học lại?\"}"),
                        finalResponse());
        when(tools.searchAcademicKnowledge(execution, "Quy chế học lại?")).thenReturn(List.of());

        var answer = adapter.answer(execution, List.of());

        assertThat(answer.content()).isEqualTo("Đã tra cứu.");
        verify(tools).getAcademicProfile(execution);
        verify(tools).searchAcademicKnowledge(execution, "Quy chế học lại?");
        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(3)).call(prompts.capture());
        assertThat(prompts.getAllValues().get(1).getInstructions()).contains(profileCall);
        assertThat(profileCall.getMetadata().get("thoughtSignatures")).isSameAs(signatures);
        var toolResponses = prompts.getAllValues().get(2).getInstructions().stream()
                .filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast)
                .flatMap(message -> message.getResponses().stream())
                .toList();
        assertThat(toolResponses)
                .extracting(ToolResponseMessage.ToolResponse::id)
                .containsExactly("p1", "k1");
        assertThat(eventNames).containsExactly("tool.started", "tool.completed", "tool.started", "tool.completed");
    }

    @Test
    void modelCannotSupplyStudentIdentityAsProfileArguments() {
        when(model.call(any(Prompt.class)))
                .thenReturn(responseWithTool("p1", "getAcademicProfile", "{\"studentId\":999}"), finalResponse());
        adapter.answer(execution, List.of());
        verifyNoInteractions(tools);
        assertThat(eventNames).isEmpty();
    }

    @Test
    void unknownToolNeverExecutesAnyBusinessService() {
        when(model.call(any(Prompt.class))).thenReturn(responseWithTool("x1", "executeSql", "{}"));
        assertThatThrownBy(() -> adapter.answer(execution, List.of())).isInstanceOf(AiAssistantException.class);
        verifyNoInteractions(tools);
    }

    @Test
    void repeatedCallsStopBeforeExceedingToolBudget() {
        when(model.call(any(Prompt.class))).thenReturn(responseWithTool("p1", "getAcademicProfile", "{}"));
        assertThatThrownBy(() -> adapter.answer(execution, List.of()))
                .isInstanceOf(AdvisorExecutionStoppedException.class);
        verify(tools, times(2)).getAcademicProfile(execution);
        verify(model, times(3)).call(any(Prompt.class));
        assertThat(execution.getStatus().state()).isEqualTo(AdvisorRequestState.LIMIT_REACHED);
        assertThat(eventNames.getLast()).isEqualTo("agent.limit_reached");
    }

    @Test
    void cancellationDuringModelCallDiscardsLateToolCalls() {
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            execution.cancel("USER_REQUEST");
            return responseWithTool("p1", "getAcademicProfile", "{}");
        });
        assertThatThrownBy(() -> adapter.answer(execution, List.of()))
                .isInstanceOf(AdvisorExecutionStoppedException.class);
        verifyNoInteractions(tools);
        assertThat(eventNames).containsExactly("agent.cancelled");
    }

    @Test
    void thoughtGenerationIsNotParsedOrReturnedAsAnswer() {
        var thought = AssistantMessage.builder()
                .content("PRIVATE_REASONING")
                .properties(Map.of("isThought", true))
                .build();
        when(model.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(
                        new Generation(thought),
                        new Generation(new AssistantMessage("{\"content\":\"Xin chào\",\"citationIds\":[]}")))));
        assertThat(adapter.answer(execution, List.of()).content()).isEqualTo("Xin chào");
    }

    @Test
    void finalJsonPartsAreCombinedOnlyWithinTheFirstVisibleCandidate() {
        var firstPart = AssistantMessage.builder()
                .content("{\"content\":\"Xin ")
                .properties(Map.of("candidateIndex", 0))
                .build();
        var secondPart = AssistantMessage.builder()
                .content("chào\",\"citationIds\":[]}")
                .properties(Map.of("candidateIndex", 0))
                .build();
        var otherCandidate = AssistantMessage.builder()
                .content("Không ghép candidate khác")
                .properties(Map.of("candidateIndex", 1))
                .build();
        when(model.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(
                        new Generation(firstPart), new Generation(secondPart), new Generation(otherCandidate))));
        assertThat(adapter.answer(execution, List.of()).content()).isEqualTo("Xin chào");
    }

    @Test
    void lastModelCallDoesNotAdvertiseFurtherTools() {
        properties.setMaximumModelCalls(2);
        when(model.call(any(Prompt.class)))
                .thenReturn(responseWithTool("p1", "getAcademicProfile", "{}"), finalResponse());
        adapter.answer(execution, List.of());
        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        assertThat(((ToolCallingChatOptions) prompts.getAllValues().getLast().getOptions()).getToolCallbacks())
                .isEmpty();
    }

    private ChatResponse responseWithTool(String id, String name, String arguments) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(id, "function", name, arguments)))
                .build())));
    }

    private ChatResponse finalResponse() {
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage("{\"content\":\"Đã tra cứu.\",\"citationIds\":[]}"))));
    }
}
