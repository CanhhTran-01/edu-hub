package com.eduhub.edu_hub.aiassistant.infrastructure.adapter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.academic.api.dto.request.GpaGoalRequest.CourseGradeConditionRequest;
import com.eduhub.edu_hub.academic.api.dto.response.AdvisorAcademicProfileResponse;
import com.eduhub.edu_hub.academic.api.dto.response.PrerequisiteImpactResponse;
import com.eduhub.edu_hub.academic.domain.enums.CourseGradeComparison;
import com.eduhub.edu_hub.academic.domain.enums.PrerequisiteAnalysisScope;
import com.eduhub.edu_hub.academic.domain.enums.PrerequisiteAnalysisStatus;
import com.eduhub.edu_hub.aiassistant.api.dto.response.AdvisorEventResponse;
import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.aiassistant.application.port.AiAdvisorModelPort.ConversationMessage;
import com.eduhub.edu_hub.aiassistant.application.service.*;
import com.eduhub.edu_hub.aiassistant.application.service.AdvisorChatExecution.AdvisorExecutionStoppedException;
import com.eduhub.edu_hub.aiassistant.domain.enums.AdvisorRequestState;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiAdvisorProperties;
import com.eduhub.edu_hub.enrollment.api.dto.response.MinimumExamScoreResponse;
import com.eduhub.edu_hub.enrollment.domain.enums.MinimumExamScoreStatus;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

        var history = List.of(new ConversationMessage("USER", "Quy chế học lại?"));
        var answer = adapter.answer(execution, history);

        assertThat(answer.content()).isEqualTo("Đã tra cứu.");
        verify(tools).getAcademicProfile(execution);
        verify(tools).searchAcademicKnowledge(execution, "Quy chế học lại?");
        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(3)).call(prompts.capture());
        assertThat(prompts.getAllValues().getFirst().getUserMessage().getText())
                .contains("\"history\"", "\"role\":\"USER\"", "\"content\":\"Quy chế học lại?\"");
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

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"query\":null}",
                "{\"query\":123}",
                "{\"query\":true}",
                "{\"query\":[]}",
                "{\"query\":\" \"}",
                "{\"query\":\"Quy chế\",\"studentId\":999}",
                "[]"
            })
    void invalidKnowledgeArgumentsAreRejectedBeforeMethodBindingAndToolTrace(String arguments) {
        when(model.call(any(Prompt.class)))
                .thenReturn(responseWithTool("k1", "searchAcademicKnowledge", arguments), finalResponse());

        assertThat(adapter.answer(execution, List.of()).content()).isEqualTo("Đã tra cứu.");

        verifyNoInteractions(tools);
        assertThat(eventNames).isEmpty();
        assertThat(execution.getStatus().toolCalls()).isZero();
    }

    @Test
    void annotatedToolSchemasExposeOnlyBusinessArguments() {
        when(model.call(any(Prompt.class))).thenReturn(finalResponse());

        adapter.answer(execution, List.of());

        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompts.capture());
        var callbacks = ((ToolCallingChatOptions) prompts.getValue().getOptions()).getToolCallbacks();
        assertThat(callbacks)
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactlyInAnyOrder(
                        "getAcademicProfile",
                        "searchAcademicKnowledge",
                        "solveGpaGoal",
                        "analyzePrerequisiteImpact",
                        "solveExamScore");
        var jsonMapper = JsonMapper.builder().build();
        for (var callback : callbacks) {
            var definition = callback.getToolDefinition();
            var schema = jsonMapper.readTree(definition.inputSchema());
            assertThat(definition.inputSchema()).doesNotContain("execution", "principal", "studentId", "userId");
            if (definition.name().equals("getAcademicProfile")) {
                assertThat(schema.get("properties").isEmpty()).isTrue();
            } else if (definition.name().equals("solveGpaGoal")) {
                assertThat(schema.get("properties").size()).isEqualTo(2);
                assertThat(schema.at("/properties/conditionChanges/type").asString())
                        .isEqualTo("array");
                assertThat(schema.at("/properties/resetConditions/type").asString())
                        .isEqualTo("boolean");
            } else if (definition.name().equals("analyzePrerequisiteImpact")) {
                assertThat(schema.get("properties").size()).isEqualTo(2);
                assertThat(schema.at("/properties/courseReferences/type").asString())
                        .isEqualTo("array");
            } else if (definition.name().equals("solveExamScore")) {
                assertThat(schema.get("properties").size()).isEqualTo(5);
                assertThat(schema.at("/properties/resetInputs/type").asString()).isEqualTo("boolean");
                assertThat(schema.get("required").toString()).doesNotContain("componentScore1", "componentScore2");
            } else {
                assertThat(schema.get("properties").size()).isEqualTo(1);
                assertThat(schema.at("/properties/query/type").asString()).isEqualTo("string");
                assertThat(schema.get("required").get(0).asString()).isEqualTo("query");
            }
        }
    }

    @Test
    void annotatedProfileToolKeepsNumericNullAndTimestampValuesInItsJsonResponse() {
        var observedAt = LocalDateTime.of(2026, 11, 27, 10, 0);
        when(tools.getAcademicProfile(execution))
                .thenReturn(new AdvisorAcademicProfileResponse(
                        11L,
                        "Học kỳ hiện tại",
                        new BigDecimal("3.25"),
                        null,
                        60,
                        120,
                        18,
                        new BigDecimal("3.20"),
                        new BigDecimal("3.50"),
                        6,
                        0,
                        true,
                        null,
                        observedAt));
        when(model.call(any(Prompt.class)))
                .thenReturn(responseWithTool("p1", "getAcademicProfile", "{}"), finalResponse());

        adapter.answer(execution, List.of());

        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        var profileResponse = prompts.getAllValues().getLast().getInstructions().stream()
                .filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast)
                .flatMap(message -> message.getResponses().stream())
                .findFirst()
                .orElseThrow();
        var profileJson = JsonMapper.builder().build().readTree(profileResponse.responseData());
        assertThat(profileJson.isObject()).isTrue();
        assertThat(profileJson.get("cumulativeGpa").isNumber()).isTrue();
        assertThat(profileJson.get("cumulativeGpa").decimalValue()).isEqualByComparingTo("3.25");
        assertThat(profileJson.get("currentSemesterGpa").isNull()).isTrue();
        assertThat(profileJson.get("observedAt").asString()).isEqualTo("2026-11-27T10:00:00");
        verify(tools).getAcademicProfile(execution);
    }

    @ParameterizedTest
    @EnumSource(
            value = ErrorCode.class,
            names = {"UNAUTHENTICATED", "UNAUTHORIZED", "FIRST_LOGIN_REQUIRED"})
    void authorizationFailureFromAnnotatedToolStopsTheAgentWithItsOriginalTraceCode(ErrorCode errorCode) {
        when(tools.getAcademicProfile(execution)).thenThrow(new AiAssistantException(errorCode));
        when(model.call(any(Prompt.class))).thenReturn(responseWithTool("p1", "getAcademicProfile", "{}"));

        assertThatThrownBy(() -> adapter.answer(execution, List.of()))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHENTICATED));

        verify(model).call(any(Prompt.class));
        assertThat(eventNames).containsExactly("tool.started", "tool.failed");
        assertThat(((Map<?, ?>) events.getLast().data()).get("code")).isEqualTo(errorCode.getCode());
    }

    @Test
    void businessFailureFromAnnotatedToolKeepsItsOriginalCodeAndAllowsTheModelToContinue() {
        when(tools.getAcademicProfile(execution))
                .thenThrow(new AiAssistantException(ErrorCode.ENROLLMENT_DATA_INCOMPLETE));
        when(model.call(any(Prompt.class)))
                .thenReturn(responseWithTool("p1", "getAcademicProfile", "{}"), finalResponse());

        assertThat(adapter.answer(execution, List.of()).content()).isEqualTo("Đã tra cứu.");

        assertThat(eventNames).containsExactly("tool.started", "tool.failed");
        assertThat(((Map<?, ?>) events.getLast().data()).get("code"))
                .isEqualTo(ErrorCode.ENROLLMENT_DATA_INCOMPLETE.getCode());
        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues().getLast().getInstructions().stream()
                        .filter(ToolResponseMessage.class::isInstance)
                        .map(ToolResponseMessage.class::cast)
                        .flatMap(message -> message.getResponses().stream())
                        .findFirst()
                        .orElseThrow()
                        .responseData())
                .contains("\"code\":" + ErrorCode.ENROLLMENT_DATA_INCOMPLETE.getCode());
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
    void cancellationDuringAnnotatedToolDiscardsItsLateResponse() {
        when(model.call(any(Prompt.class))).thenReturn(responseWithTool("p1", "getAcademicProfile", "{}"));
        when(tools.getAcademicProfile(execution)).thenAnswer(invocation -> {
            execution.cancel("USER_REQUEST");
            return null;
        });

        assertThatThrownBy(() -> adapter.answer(execution, List.of()))
                .isInstanceOf(AdvisorExecutionStoppedException.class);

        verify(model).call(any(Prompt.class));
        assertThat(eventNames).containsExactly("tool.started", "agent.cancelled");
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

    @Test
    void simulationCommentUsesItsOwnPromptWithoutToolsOrConversationHistory() {
        properties.setSimulationCommentPrompt("Chỉ nhận xét định tính cho popup.");
        when(model.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(
                        new AssistantMessage("Hãy cân nhắc bộ giả định theo kế hoạch học tập của bạn.")))));
        assertThat(adapter.commentOnSimulation("{\"semesterGpa\":3.25}"))
                .isEqualTo("Hãy cân nhắc bộ giả định theo kế hoạch học tập của bạn.");
        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompts.capture());
        assertThat(prompts.getValue().getSystemMessage().getText()).isEqualTo("Chỉ nhận xét định tính cho popup.");
        assertThat(((ToolCallingChatOptions) prompts.getValue().getOptions()).getToolCallbacks())
                .isEmpty();
        verifyNoInteractions(tools);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GPA sẽ là 9.99.", "<script>test</script>", "```json\n{}\n```", " "})
    void simulationCommentRejectsUnverifiedNumbersOrMarkup(String comment) {
        properties.setSimulationCommentPrompt("Chỉ nhận xét định tính cho popup.");
        when(model.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(comment)))));
        assertThatThrownBy(() -> adapter.commentOnSimulation("{}")).isInstanceOf(AiAssistantException.class);
    }

    private ChatResponse finalResponse() {
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage("{\"content\":\"Đã tra cứu.\",\"citationIds\":[]}"))));
    }

    @Test
    void annotatedGpaToolReceivesAllConditionsAndEmitsActualExecutionTrace() {
        when(model.call(any(Prompt.class)))
                .thenReturn(
                        responseWithTool(
                                "g1",
                                "solveGpaGoal",
                                """
                {"conditionChanges":[
                  {"courseReference":" X ","comparison":"EXACT","gradeLetter":"b"},
                  {"courseReference":"Y","comparison":"AT_MOST","gradeLetter":"C"}
                ],"resetConditions":false}
                """),
                        finalResponse());
        adapter.answer(execution, List.of());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CourseGradeConditionRequest>> conditions = ArgumentCaptor.forClass(List.class);
        verify(tools).solveGpaGoal(eq(execution), conditions.capture(), eq(false));
        assertThat(conditions.getValue())
                .containsExactly(
                        new CourseGradeConditionRequest("X", null, CourseGradeComparison.EXACT, "B"),
                        new CourseGradeConditionRequest("Y", null, CourseGradeComparison.AT_MOST, "C"));
        assertThat(eventNames).containsExactly("tool.started", "tool.completed");
        assertThat(((Map<?, ?>) events.getFirst().data()).get("toolName")).isEqualTo("solveGpaGoal");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"conditionChanges\":[],\"resetConditions\":false,\"studentId\":99}",
                "{\"conditionChanges\":[],\"resetConditions\":\"false\"}",
                "{\"conditionChanges\":[{\"enrollmentId\":1.5,\"comparison\":\"EXACT\",\"gradeLetter\":\"B\"}],\"resetConditions\":false}",
                "{\"conditionChanges\":[{\"courseReference\":\"X\",\"comparison\":\"EXACT\",\"gradeLetter\":\"B\",\"semesterId\":1}],\"resetConditions\":false}"
            })
    void invalidGpaArgumentsNeverExecuteTheToolOrEmitAFakeStartedEvent(String arguments) {
        when(model.call(any(Prompt.class)))
                .thenReturn(responseWithTool("g1", "solveGpaGoal", arguments), finalResponse());
        adapter.answer(execution, List.of());
        verifyNoInteractions(tools);
        assertThat(eventNames).isEmpty();
    }

    @Test
    void examToolBindsDecimalScoresAndLeavesMissingFollowUpInputsNull() {
        when(model.call(any(Prompt.class)))
                .thenReturn(
                        responseWithTool(
                                "e1",
                                "solveExamScore",
                                """
                        {"subjectName":" Giải tích 1 ","componentScore1":8,"componentScore2":7.25,
                         "targetGradeLetter":"A","resetInputs":true}
                        """),
                        responseWithTool("e2", "solveExamScore", "{\"componentScore2\":0,\"resetInputs\":false}"),
                        finalResponse());

        adapter.answer(execution, List.of());

        verify(tools)
                .solveExamScore(
                        eq(execution),
                        eq("Giải tích 1"),
                        eq(new BigDecimal("8")),
                        eq(new BigDecimal("7.25")),
                        eq("A"),
                        eq(true));
        verify(tools).solveExamScore(execution, null, null, BigDecimal.ZERO, null, false);
        assertThat(eventNames).containsExactly("tool.started", "tool.completed", "tool.started", "tool.completed");
    }

    @Test
    void prerequisiteToolBindsMultipleSubjectReferencesWithoutAcceptingStudentIdentity() {
        when(model.call(any(Prompt.class)))
                .thenReturn(
                        responseWithTool(
                                "p1",
                                "analyzePrerequisiteImpact",
                                """
                {"scope":"COURSE_IMPACT","courseReferences":[" GT1 ","Vật lý 1"]}
                """),
                        finalResponse());

        adapter.answer(execution, List.of());

        verify(tools)
                .analyzePrerequisiteImpact(
                        execution, PrerequisiteAnalysisScope.COURSE_IMPACT, List.of("GT1", "Vật lý 1"));
        assertThat(eventNames).containsExactly("tool.started", "tool.completed");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"resetInputs\":false,\"studentId\":99}",
                "{\"componentScore1\":\"8\",\"resetInputs\":false}",
                "{\"componentScore2\":true,\"resetInputs\":false}",
                "{\"componentScore1\":8}",
                "{\"subjectName\":[],\"resetInputs\":false}",
                "{\"resetInputs\":\"false\"}"
            })
    void invalidExamArgumentsDoNotInvokeTheToolOrEmitAnExecutionTrace(String arguments) {
        when(model.call(any(Prompt.class)))
                .thenReturn(responseWithTool("e1", "solveExamScore", arguments), finalResponse());
        adapter.answer(execution, List.of());
        verifyNoInteractions(tools);
        assertThat(eventNames).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"scope\":\"COURSE_IMPACT\",\"courseReferences\":[99]}",
                "{\"scope\":\"SEMESTER_OVERVIEW\",\"courseReferences\":[\"X\"]}",
                "{\"scope\":\"COURSE_IMPACT\",\"courseReferences\":[],\"semesterId\":99}",
                "{\"scope\":\"UNKNOWN\",\"courseReferences\":[]}",
                "{\"scope\":\"COURSE_IMPACT\",\"courseReferences\":[\" \"]}"
            })
    void invalidPrerequisiteArgumentsDoNotInvokeTheToolOrEmitAnExecutionTrace(String arguments) {
        when(model.call(any(Prompt.class)))
                .thenReturn(responseWithTool("p1", "analyzePrerequisiteImpact", arguments), finalResponse());
        adapter.answer(execution, List.of());
        verifyNoInteractions(tools);
        assertThat(eventNames).isEmpty();
    }

    @Test
    void reopenedConversationPinsTypedInputsAndCurrentToolResponseUsesACompactSummary() {
        var priorExam = MinimumExamScoreResponse.builder()
                .status(MinimumExamScoreStatus.SOLVED)
                .subjectName("Giải tích 1")
                .componentScore1(new BigDecimal("8"))
                .componentScore2(new BigDecimal("7"))
                .targetGradeLetter("A")
                .calculatedAt(LocalDateTime.now(clock))
                .build();
        var priorPrerequisites = PrerequisiteImpactResponse.builder()
                .status(PrerequisiteAnalysisStatus.COMPLETED)
                .scope(PrerequisiteAnalysisScope.COURSE_IMPACT)
                .courseReferences(List.of("GT1"))
                .pendingCourseReferences(List.of())
                .courses(List.of())
                .calculatedAt(LocalDateTime.now(clock))
                .build();
        execution = new AdvisorChatExecution(
                UUID.randomUUID(),
                7L,
                execution.getPrincipal(),
                "TP2 là 8 thì sao?",
                new ChatHistoryService.ConversationContext(3L, 11L, List.of(), null, priorPrerequisites, priorExam),
                properties,
                counter,
                clock);
        var newExam = MinimumExamScoreResponse.builder()
                .status(MinimumExamScoreStatus.SOLVED)
                .subjectName("Giải tích 1")
                .componentScore1(new BigDecimal("8"))
                .componentScore2(new BigDecimal("8"))
                .targetGradeLetter("A")
                .minimumExamScore(new BigDecimal("8.643"))
                .calculatedAt(LocalDateTime.now(clock))
                .build();
        when(tools.solveExamScore(execution, null, null, new BigDecimal("8"), null, false))
                .thenAnswer(invocation -> {
                    execution.recordExamScore(newExam);
                    return newExam;
                });
        when(model.call(any(Prompt.class)))
                .thenReturn(
                        responseWithTool("e1", "solveExamScore", "{\"componentScore2\":8,\"resetInputs\":false}"),
                        finalResponse());

        adapter.answer(execution, List.of());

        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        var jsonMapper = JsonMapper.builder().build();
        var initial = jsonMapper.readTree(
                prompts.getAllValues().getFirst().getUserMessage().getText());
        assertThat(initial.at("/historicalExamContext/componentScore2").intValue())
                .isEqualTo(7);
        assertThat(initial.at("/historicalPrerequisiteContext/courseReferences/0")
                        .asString())
                .isEqualTo("GT1");
        var toolResponse = prompts.getAllValues().getLast().getInstructions().stream()
                .filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast)
                .flatMap(message -> message.getResponses().stream())
                .findFirst()
                .orElseThrow();
        var summary = jsonMapper.readTree(toolResponse.responseData());
        assertThat(summary.get("minimumExamScore").decimalValue()).isEqualByComparingTo("8.643");
        assertThat(summary.has("gradeScale")).isFalse();
        assertThat(execution.getExamScore()).isSameAs(newExam);
    }
}
