package com.eduhub.edu_hub.aiassistant.infrastructure.adapter;

import static com.eduhub.edu_hub.aiassistant.infrastructure.adapter.AcademicAdvisorTools.GPA_GOAL_TOOL;
import static com.eduhub.edu_hub.aiassistant.infrastructure.adapter.AcademicAdvisorTools.KNOWLEDGE_TOOL;
import static com.eduhub.edu_hub.aiassistant.infrastructure.adapter.AcademicAdvisorTools.PROFILE_TOOL;

import com.eduhub.edu_hub.academic.api.dto.request.GpaGoalRequest;
import com.eduhub.edu_hub.academic.api.dto.request.GpaGoalRequest.CourseGradeConditionRequest;
import com.eduhub.edu_hub.academic.domain.enums.CourseGradeComparison;
import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.aiassistant.application.port.AiAdvisorModelPort;
import com.eduhub.edu_hub.aiassistant.application.port.AiAdvisorModelPort.*;
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
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Vòng do ứng dụng điều khiển để kiểm tra ngân sách trước từng model/tool call.
 * Spring AI giữ protocol và metadata chữ ký Gemini; không bật thêm loop ChatClient.
 */
@Component
public class SpringAiAdvisorModelAdapter implements AiAdvisorModelPort {
    private static final int MAXIMUM_GPA_ARGUMENT_CHARACTERS = 400000;
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
        // 1. Khởi tạo context cho AI.
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(properties.requireSystemPrompt()));
        var context = new LinkedHashMap<String, Object>();
        context.put("question", execution.getQuestion());
        context.put("history", history);
        // Trạng thái chính xác nằm ngoài sliding window nhưng vẫn được tính vào ngân sách toàn prompt.
        context.put("historicalGpaContext", execution.getGpaContext());
        messages.add(new UserMessage(objectMapper.writeValueAsString(context)));

        // 2. Khởi tạo hàng đợi Tool Call và đăng ký các Tool cho AI.
        Deque<AssistantMessage.ToolCall> pendingCalls = new ArrayDeque<>();
        var tools = Arrays.stream(ToolCallbacks.from(new AcademicAdvisorTools(toolService, execution)))
                .<ToolCallback>map(toolCallback -> new AdvisorToolCallback(toolCallback, execution, pendingCalls))
                .toList();

        // 3. Bắt đầu vòng lặp thực thi AI Agent.
        while (true) {

            // 4. Kiểm tra giới hạn thực thi và cấu hình Model.
            execution.checkCanContinue();
            boolean offerTools = execution.shouldOfferTools();
            var options = ToolCallingChatOptions.builder()
                    .temperature(0.0)
                    .maxTokens(execution.getMaximumOutputTokens())
                    .toolCallbacks(offerTools ? tools : List.of())
                    .build();

            // 5. Tạo Prompt, kiểm soát ngân sách và gọi Model.
            var prompt = new Prompt(messages, options);
            execution.beforeModelCall(serializeBudgetContext(messages, offerTools ? tools : List.of()));
            ChatResponse modelResponse = chatModel.call(prompt);
            execution.checkCanContinue();

            // 6. Kiểm tra tính hợp lệ của Model Response.
            // NOTE: Annotation của SDK không thay kiểm tra runtime tại ranh giới nhận dữ liệu bên ngoài.
            if (modelResponse == null || modelResponse.getResults().isEmpty())
                throw new AiAssistantException(ErrorCode.AI_SERVICE_UNAVAILABLE);

            // 7. Xử lý nhánh Tool Calling nếu AI yêu cầu thu thập thêm dữ liệu.
            if (modelResponse.hasToolCalls()) {
                if (!offerTools) {
                    execution.limit("MODEL_CALL_BUDGET");
                    throw new AdvisorExecutionStoppedException();
                }

                // 8. Trích xuất, kiểm tra và đưa các Tool Call hợp lệ vào hàng đợi.
                var toolGeneration = modelResponse.getResults().stream()
                        .filter(generation ->
                                !generation.getOutput().getToolCalls().isEmpty())
                        .findFirst()
                        .orElseThrow();
                pendingCalls.clear();
                for (var call : toolGeneration.getOutput().getToolCalls()) {
                    if (!Set.of(PROFILE_TOOL, KNOWLEDGE_TOOL, GPA_GOAL_TOOL).contains(call.name()))
                        throw new AiAssistantException(ErrorCode.AI_SERVICE_UNAVAILABLE);
                    pendingCalls.add(call);
                }

                // 9. Thực thi Tool, cập nhật context và quay lại vòng lặp Agent.
                // Giữ nguyên AssistantMessage và metadata từ provider, không dựng lại chỉ từ text/arguments.
                messages = toolCallingManager
                        .executeToolCalls(prompt, modelResponse)
                        .conversationHistory();
                continue;
            }

            // 10. Xử lý nhánh Final Answer và loại bỏ nội dung suy luận của Model.
            var visibleOutputs = modelResponse.getResults().stream()
                    .map(Generation::getOutput)
                    .filter(output -> !Boolean.TRUE.equals(output.getMetadata().get("isThought")))
                    .toList();
            if (visibleOutputs.isEmpty()) throw new AiAssistantException(ErrorCode.AI_SERVICE_UNAVAILABLE);

            // 11. Ghép các phần nội dung thuộc cùng Candidate thành câu trả lời hoàn chỉnh.
            var firstOutput = visibleOutputs.getFirst();
            Object candidateIndex = firstOutput.getMetadata().get("candidateIndex");
            String finalContent = candidateIndex == null
                    ? Objects.requireNonNullElse(firstOutput.getText(), "")
                    : visibleOutputs.stream()
                            .filter(output -> Objects.equals(
                                    candidateIndex, output.getMetadata().get("candidateIndex")))
                            .map(AssistantMessage::getText)
                            .filter(Objects::nonNull)
                            .reduce("", String::concat);

            // 12. Parse câu trả lời và kết thúc vòng lặp Agent.
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

    @Override
    public String commentOnSimulation(String verifiedSimulationJson) {
        var options = ToolCallingChatOptions.builder()
                .temperature(0.0)
                .maxTokens(properties.getMaximumOutputTokens())
                .toolCallbacks(List.of())
                .build();
        var response = chatModel.call(new Prompt(
                List.of(
                        new SystemMessage(properties.requireSimulationCommentPrompt()),
                        new UserMessage(verifiedSimulationJson)),
                options));
        if (response == null || response.hasToolCalls() || response.getResults().isEmpty())
            throw new AiAssistantException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        var visibleOutputs = response.getResults().stream()
                .map(Generation::getOutput)
                .filter(output -> !Boolean.TRUE.equals(output.getMetadata().get("isThought")))
                .toList();
        if (visibleOutputs.isEmpty()) throw new AiAssistantException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        var firstOutput = visibleOutputs.getFirst();
        Object candidateIndex = firstOutput.getMetadata().get("candidateIndex");
        String comment = candidateIndex == null
                ? Objects.requireNonNullElse(firstOutput.getText(), "")
                : visibleOutputs.stream()
                        .filter(output -> Objects.equals(
                                candidateIndex, output.getMetadata().get("candidateIndex")))
                        .map(AssistantMessage::getText)
                        .filter(Objects::nonNull)
                        .reduce("", String::concat);
        comment = AiContentPolicy.redactPromptText(comment).strip();
        // Số liệu trong lời thoại do template BE phát; model chỉ nhận xét định tính, không thêm số chưa kiểm chứng.
        if (comment.isBlank()
                || comment.length() > properties.getMaximumSimulationCommentCharacters()
                || comment.codePoints().anyMatch(Character::isDigit)
                || comment.contains("```")
                || comment.contains("<")
                || comment.contains(">")) throw new AiAssistantException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        return comment.replaceAll("\\s+", " ");
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

    private String canonicalGpaArguments(JsonNode arguments) {
        if (arguments.size() != 2
                || !arguments.has("conditionChanges")
                || !arguments.has("resetConditions")
                || !arguments.get("resetConditions").isBoolean()) throw new IllegalArgumentException();
        var requestedConditions = arguments.get("conditionChanges");
        if (!requestedConditions.isArray() || requestedConditions.size() > GpaGoalRequest.MAXIMUM_CONDITIONS)
            throw new IllegalArgumentException();
        var conditions = new ArrayList<CourseGradeConditionRequest>();
        for (var condition : requestedConditions) {
            if (!condition.isObject()) throw new IllegalArgumentException();
            for (String property : condition.propertyNames()) {
                if (!Set.of("courseReference", "enrollmentId", "comparison", "gradeLetter")
                        .contains(property)) throw new IllegalArgumentException();
            }
            var reference = condition.get("courseReference");
            if (reference != null
                    && !reference.isNull()
                    && (!reference.isString() || reference.asString().length() > 255))
                throw new IllegalArgumentException();
            var enrollmentId = condition.get("enrollmentId");
            if (enrollmentId != null
                    && !enrollmentId.isNull()
                    && (!enrollmentId.isIntegralNumber()
                            || !enrollmentId.canConvertToLong()
                            || enrollmentId.longValue() <= 0)) throw new IllegalArgumentException();
            if ((enrollmentId == null || enrollmentId.isNull())
                    && (reference == null
                            || !reference.isString()
                            || reference.asString().isBlank())) throw new IllegalArgumentException();
            var comparison = condition.get("comparison");
            if (comparison == null || !comparison.isString()) throw new IllegalArgumentException();
            var relation = CourseGradeComparison.valueOf(comparison.asString());
            var letter = condition.get("gradeLetter");
            if (letter != null
                    && !letter.isNull()
                    && (!letter.isString() || letter.asString().length() > 10)) throw new IllegalArgumentException();
            if (relation != CourseGradeComparison.REMOVE
                    && (letter == null
                            || !letter.isString()
                            || letter.asString().isBlank())) throw new IllegalArgumentException();
            conditions.add(new CourseGradeConditionRequest(
                    reference == null || reference.isNull()
                            ? null
                            : AiContentPolicy.redactPromptText(AiContentPolicy.normalizeQuestion(reference.asString())),
                    enrollmentId == null || enrollmentId.isNull() ? null : enrollmentId.longValue(),
                    relation,
                    letter == null || letter.isNull()
                            ? null
                            : letter.asString().strip().toUpperCase(Locale.ROOT)));
        }
        return objectMapper.writeValueAsString(Map.of(
                "conditionChanges",
                conditions,
                "resetConditions",
                arguments.get("resetConditions").booleanValue()));
    }

    private final class AdvisorToolCallback implements ToolCallback {
        private final ToolCallback toolCallback;
        private final AdvisorChatExecution execution;
        private final Deque<AssistantMessage.ToolCall> pendingCalls;

        private AdvisorToolCallback(
                ToolCallback toolCallback,
                AdvisorChatExecution execution,
                Deque<AssistantMessage.ToolCall> pendingCalls) {
            this.toolCallback = toolCallback;
            this.execution = execution;
            this.pendingCalls = pendingCalls;
        }

        @Override
        public @NonNull ToolDefinition getToolDefinition() {
            return toolCallback.getToolDefinition();
        }

        @Override
        public @NonNull ToolMetadata getToolMetadata() {
            return toolCallback.getToolMetadata();
        }

        @Override
        public @NonNull String call(@NonNull String arguments) {
            // 1. Kiểm tra trạng thái thực thi và đối chiếu Tool Call với hàng đợi.
            execution.checkCanContinue();
            var call = pendingCalls.removeFirst();
            String toolName = getToolDefinition().name();
            if (!toolName.equals(call.name())) throw new AiAssistantException(ErrorCode.AI_SERVICE_UNAVAILABLE);

            // 2. Validate arguments do AI cung cấp theo schema của từng Tool.
            String query = null;
            String canonicalArguments;
            try {
                if (arguments == null
                        || arguments.length()
                                > (GPA_GOAL_TOOL.equals(toolName) ? MAXIMUM_GPA_ARGUMENT_CHARACTERS : 5000))
                    throw new IllegalArgumentException();
                var parsedArguments = objectMapper.readTree(arguments);
                if (!parsedArguments.isObject()) throw new IllegalArgumentException();

                // PROFILE_TOOL không nhận tham số, KNOWLEDGE_TOOL yêu cầu query hợp lệ.
                if (PROFILE_TOOL.equals(toolName)) {
                    if (!parsedArguments.isEmpty()) throw new IllegalArgumentException();
                    query = null;
                    canonicalArguments = "{}";
                } else if (GPA_GOAL_TOOL.equals(toolName)) {
                    canonicalArguments = canonicalGpaArguments(parsedArguments);
                } else {
                    if (parsedArguments.size() != 1
                            || !parsedArguments.has("query")
                            || !parsedArguments.get("query").isString()) throw new IllegalArgumentException();
                    query = parsedArguments.get("query").asString();
                    if (query.isBlank() || query.length() > 2000) throw new IllegalArgumentException();
                    query = AiContentPolicy.redactPromptText(AiContentPolicy.normalizeQuestion(query));
                    canonicalArguments = objectMapper.writeValueAsString(Map.of("query", query));
                }
            } catch (RuntimeException exception) {
                // Chưa chạy tool khi validation bị từ chối, không phát trace giả.
                return "{\"error\":{\"code\":1001,\"message\":\"Arguments không hợp lệ; chỉ dùng đúng schema tool.\"}}";
            }

            // 3. Chuẩn hóa thông tin Tool Call và ghi nhận sự kiện bắt đầu thực thi.
            String traceCallId =
                    call.id() == null || call.id().isBlank() ? UUID.randomUUID().toString() : call.id();
            execution.startTool(
                    traceCallId,
                    toolName,
                    canonicalArguments,
                    GPA_GOAL_TOOL.equals(toolName)
                            ? "Đang tìm phương án đạt mục tiêu GPA kỳ…"
                            : query == null ? "Đang tra cứu hồ sơ học vụ cá nhân…" : "Đang tra cứu tài liệu học vụ…");

            // 4. Thực thi Tool tương ứng để lấy dữ liệu học vụ.
            try {
                // 5. Kiểm tra trạng thái, serialize kết quả và ghi nhận Tool hoàn thành.
                String toolJson = toolCallback.call(canonicalArguments);
                execution.checkCanContinue();
                execution.completeTool(traceCallId, toolName);
                return toolJson;

                // 6. Lan truyền exception nếu Agent bị yêu cầu dừng thực thi.
            } catch (AdvisorExecutionStoppedException exception) {
                throw exception;

                // 7. Xử lý lỗi trong quá trình thực thi Tool và ghi nhận Tool thất bại.
            } catch (RuntimeException exception) {
                execution.checkCanContinue();
                // Spring AI bọc lỗi từ method @Tool; lấy lại nguyên nhân để giữ mã lỗi và quy tắc dừng hiện có.
                Throwable toolFailure = exception;
                while (toolFailure instanceof ToolExecutionException && toolFailure.getCause() != null) {
                    toolFailure = toolFailure.getCause();
                }
                if (toolFailure instanceof AdvisorExecutionStoppedException stoppedException) throw stoppedException;
                int code = toolFailure instanceof BaseException businessException
                        ? businessException.getErrorCode().getCode()
                        : ErrorCode.AI_SERVICE_UNAVAILABLE.getCode();
                execution.failTool(traceCallId, toolName, code);

                // 8. Dừng Agent nếu gặp lỗi xác thực hoặc phân quyền.
                if (code == ErrorCode.UNAUTHENTICATED.getCode()
                        || code == ErrorCode.UNAUTHORIZED.getCode()
                        || code == ErrorCode.FIRST_LOGIN_REQUIRED.getCode())
                    throw new AiAssistantException(ErrorCode.UNAUTHENTICATED);

                // 9. Trả thông tin lỗi cho AI để tránh suy đoán dữ liệu khi Tool thất bại.
                return objectMapper.writeValueAsString(Map.of(
                        "error",
                        Map.of("code", code, "message", "Chưa lấy được dữ liệu, không được suy đoán kết quả.")));
            }
        }
    }
}
