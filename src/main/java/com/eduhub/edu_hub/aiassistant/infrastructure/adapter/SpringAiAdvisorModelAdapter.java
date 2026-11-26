package com.eduhub.edu_hub.aiassistant.infrastructure.adapter;

import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.aiassistant.application.port.AiAdvisorModelPort;
import com.eduhub.edu_hub.aiassistant.application.port.AiAssistantPort.*;
import com.eduhub.edu_hub.aiassistant.application.service.AdvisorChatExecution;
import com.eduhub.edu_hub.aiassistant.application.service.AdvisorChatExecution.AdvisorExecutionStoppedException;
import com.eduhub.edu_hub.aiassistant.application.service.AdvisorToolService;
import com.eduhub.edu_hub.aiassistant.domain.policy.AiContentPolicy;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiAdvisorProperties;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.*;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Vòng do ứng dụng điều khiển để kiểm tra ngân sách trước từng model/tool call.
 * Spring AI giữ protocol và metadata chữ ký Gemini; không bật thêm loop ChatClient.
 */
@Component
public class SpringAiAdvisorModelAdapter implements AiAdvisorModelPort {
    private static final String PROFILE_TOOL = "getAcademicProfile";
    private static final String KNOWLEDGE_TOOL = "searchAcademicKnowledge";
    private final ChatModel chatModel;
    private final AdvisorToolService toolService;
    private final ObjectMapper objectMapper;
    private final AiAdvisorProperties properties;
    private final ToolCallingManager toolCallingManager =
            ToolCallingManager.builder().build();

    public SpringAiAdvisorModelAdapter(
            ChatModel chatModel,
            AdvisorToolService toolService,
            ObjectMapper objectMapper,
            AiAdvisorProperties properties) {
        this.chatModel = chatModel;
        this.toolService = toolService;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Answer answer(AdvisorChatExecution execution, List<ConversationMessage> history) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(properties.requireSystemPrompt()));
        // History là dữ liệu hiểu câu hỏi, không phải chỉ dẫn hoặc bằng chứng học vụ.
        messages.add(new UserMessage(
                objectMapper.writeValueAsString(Map.of("question", execution.getQuestion(), "history", history))));
        Deque<AssistantMessage.ToolCall> pendingCalls = new ArrayDeque<>();
        var tools = List.<ToolCallback>of(
                new AdvisorToolCallback(
                        PROFILE_TOOL,
                        "Đọc GPA, tín chỉ, mục tiêu và độ đầy đủ lịch sử của chính sinh viên hiện tại. Không nhận danh tính.",
                        "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}",
                        execution,
                        pendingCalls),
                new AdvisorToolCallback(
                        KNOWLEDGE_TOOL,
                        "Tra cứu quy chế, quy định và thủ tục học vụ, trả đoạn trích cùng mã nguồn. "
                                + "Dựng query đầy đủ từ ngữ cảnh; thiếu đối tượng thì hỏi rõ, không tự đoán.",
                        "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\",\"maxLength\":2000}},"
                                + "\"required\":[\"query\"],\"additionalProperties\":false}",
                        execution,
                        pendingCalls));
        while (true) {
            execution.checkCanContinue();
            boolean offerTools = execution.shouldOfferTools();
            var options = ToolCallingChatOptions.builder()
                    .temperature(0.0)
                    .maxTokens(execution.getMaximumOutputTokens())
                    .toolCallbacks(offerTools ? tools : List.of())
                    .build();
            var prompt = new Prompt(messages, options);
            execution.beforeModelCall(serializeBudgetContext(messages, offerTools ? tools : List.of()));
            ChatResponse modelResponse = chatModel.call(prompt);
            execution.checkCanContinue();
            // NOTE: Annotation của SDK không thay kiểm tra runtime tại ranh giới nhận dữ liệu bên ngoài.
            if (modelResponse == null || modelResponse.getResults().isEmpty())
                throw new AiAssistantException(ErrorCode.AI_SERVICE_UNAVAILABLE);
            if (modelResponse.hasToolCalls()) {
                if (!offerTools) {
                    execution.limit("MODEL_CALL_BUDGET");
                    throw new AdvisorExecutionStoppedException();
                }
                var toolGeneration = modelResponse.getResults().stream()
                        .filter(generation ->
                                !generation.getOutput().getToolCalls().isEmpty())
                        .findFirst()
                        .orElseThrow();
                pendingCalls.clear();
                for (var call : toolGeneration.getOutput().getToolCalls()) {
                    if (!Set.of(PROFILE_TOOL, KNOWLEDGE_TOOL).contains(call.name()))
                        throw new AiAssistantException(ErrorCode.AI_SERVICE_UNAVAILABLE);
                    pendingCalls.add(call);
                }
                // Giữ nguyên AssistantMessage và metadata từ provider, không dựng lại chỉ từ text/arguments.
                messages = toolCallingManager
                        .executeToolCalls(prompt, modelResponse)
                        .conversationHistory();
                continue;
            }
            var visibleOutputs = modelResponse.getResults().stream()
                    .map(Generation::getOutput)
                    .filter(output -> !Boolean.TRUE.equals(output.getMetadata().get("isThought")))
                    .toList();
            if (visibleOutputs.isEmpty()) throw new AiAssistantException(ErrorCode.AI_SERVICE_UNAVAILABLE);
            var firstOutput = visibleOutputs.getFirst();
            Object candidateIndex = firstOutput.getMetadata().get("candidateIndex");
            // Gemini trả mỗi text part thành một Generation; ghép đúng candidate, bỏ phần suy luận.
            String finalContent = candidateIndex == null
                    ? Objects.requireNonNullElse(firstOutput.getText(), "")
                    : visibleOutputs.stream()
                            .filter(output -> Objects.equals(
                                    candidateIndex, output.getMetadata().get("candidateIndex")))
                            .map(AssistantMessage::getText)
                            .filter(Objects::nonNull)
                            .reduce("", String::concat);
            return parseAnswer(finalContent);
        }
    }

    private String serializeBudgetContext(List<Message> messages, List<ToolCallback> tools) {
        var contextText = new StringBuilder();
        for (var message : messages) {
            contextText
                    .append(message.getMessageType())
                    .append(':')
                    .append(message.getText())
                    .append('\n');
            if (message instanceof AssistantMessage assistant)
                contextText.append(objectMapper.writeValueAsString(assistant.getToolCalls()));
            if (message instanceof ToolResponseMessage toolResponse)
                contextText.append(objectMapper.writeValueAsString(toolResponse.getResponses()));
        }
        tools.forEach(tool -> contextText.append(objectMapper.writeValueAsString(tool.getToolDefinition())));
        return contextText.toString();
    }

    private Answer parseAnswer(String finalContent) {
        String jsonAnswer = finalContent.strip();
        // Chỉ gỡ fence ở hai đầu, không tìm JSON tùy ý trong một đoạn văn.
        String fence = String.valueOf((char) 96).repeat(3);
        if (jsonAnswer.startsWith(fence) && jsonAnswer.endsWith(fence)) {
            int firstLineEnd = jsonAnswer.indexOf('\n');
            if (firstLineEnd < 0) throw new AiAssistantException(ErrorCode.AI_SERVICE_UNAVAILABLE);
            jsonAnswer = jsonAnswer
                    .substring(firstLineEnd + 1, jsonAnswer.length() - 3)
                    .strip();
        }
        try {
            return objectMapper.readValue(jsonAnswer, Answer.class);
        } catch (RuntimeException exception) {
            throw new AiAssistantException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    private final class AdvisorToolCallback implements ToolCallback {
        private final ToolDefinition definition;
        private final AdvisorChatExecution execution;
        private final Deque<AssistantMessage.ToolCall> pendingCalls;

        private AdvisorToolCallback(
                String name,
                String description,
                String schema,
                AdvisorChatExecution execution,
                Deque<AssistantMessage.ToolCall> pendingCalls) {
            this.definition = ToolDefinition.builder()
                    .name(name)
                    .description(description)
                    .inputSchema(schema)
                    .build();
            this.execution = execution;
            this.pendingCalls = pendingCalls;
        }

        @Override
        public @NonNull ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public @NonNull String call(@NonNull String arguments) {
            execution.checkCanContinue();
            var call = pendingCalls.removeFirst();
            if (!definition.name().equals(call.name()))
                throw new AiAssistantException(ErrorCode.AI_SERVICE_UNAVAILABLE);
            String query;
            try {
                if (arguments == null || arguments.length() > 5000) throw new IllegalArgumentException();
                var parsedArguments = objectMapper.readTree(arguments);
                if (!parsedArguments.isObject()) throw new IllegalArgumentException();
                if (PROFILE_TOOL.equals(definition.name())) {
                    if (!parsedArguments.isEmpty()) throw new IllegalArgumentException();
                    query = null;
                } else {
                    if (parsedArguments.size() != 1
                            || !parsedArguments.has("query")
                            || !parsedArguments.get("query").isString()) throw new IllegalArgumentException();
                    query = parsedArguments.get("query").asString();
                    if (query.isBlank() || query.length() > 2000) throw new IllegalArgumentException();
                    query = AiContentPolicy.redactPromptText(AiContentPolicy.normalizeQuestion(query));
                }
            } catch (RuntimeException exception) {
                // Chưa chạy tool khi validation bị từ chối, không phát trace giả.
                return "{\"error\":{\"code\":1001,\"message\":\"Arguments không hợp lệ; chỉ dùng đúng schema tool.\"}}";
            }
            String traceCallId =
                    call.id() == null || call.id().isBlank() ? UUID.randomUUID().toString() : call.id();
            String canonicalArguments = query == null ? "{}" : objectMapper.writeValueAsString(Map.of("query", query));
            execution.startTool(
                    traceCallId,
                    definition.name(),
                    canonicalArguments,
                    query == null ? "Đang tra cứu hồ sơ học vụ cá nhân…" : "Đang tra cứu tài liệu học vụ…");
            try {
                Object toolResponse = query == null
                        ? toolService.getAcademicProfile(execution)
                        : toolService.searchAcademicKnowledge(execution, query);
                execution.checkCanContinue();
                String toolJson = objectMapper.writeValueAsString(toolResponse);
                execution.completeTool(traceCallId, definition.name());
                return toolJson;
            } catch (AdvisorExecutionStoppedException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                execution.checkCanContinue();
                int code = exception instanceof BaseException businessException
                        ? businessException.getErrorCode().getCode()
                        : ErrorCode.AI_SERVICE_UNAVAILABLE.getCode();
                execution.failTool(traceCallId, definition.name(), code);
                if (code == ErrorCode.UNAUTHENTICATED.getCode()
                        || code == ErrorCode.UNAUTHORIZED.getCode()
                        || code == ErrorCode.FIRST_LOGIN_REQUIRED.getCode())
                    throw new AiAssistantException(ErrorCode.UNAUTHENTICATED);
                return objectMapper.writeValueAsString(Map.of(
                        "error",
                        Map.of("code", code, "message", "Chưa lấy được dữ liệu, không được suy đoán kết quả.")));
            }
        }
    }
}
