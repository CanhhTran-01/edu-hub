package com.eduhub.edu_hub.aiassistant.application.service;

import com.eduhub.edu_hub.academic.api.dto.response.AdvisorAcademicProfileResponse;
import com.eduhub.edu_hub.academic.api.dto.response.GpaGoalResponse;
import com.eduhub.edu_hub.academic.api.dto.response.PrerequisiteImpactResponse;
import com.eduhub.edu_hub.aiassistant.api.dto.response.*;
import com.eduhub.edu_hub.aiassistant.application.port.KnowledgeChunkPort.RetrievedChunk;
import com.eduhub.edu_hub.aiassistant.domain.enums.AdvisorRequestState;
import com.eduhub.edu_hub.aiassistant.infrastructure.adapter.AiTokenCounter;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiAdvisorProperties;
import com.eduhub.edu_hub.enrollment.api.dto.response.MinimumExamScoreResponse;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Supplier;
import lombok.Getter;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Một lượt Agent: ngân sách, kết quả tool và lifecycle nằm riêng theo request.
 * Cùng một khóa bảo vệ hủy và chốt lịch sử, không giữ khóa này trong lúc gọi model hoặc tool.
 */
public final class AdvisorChatExecution {
    // Định danh request và thông tin người dùng
    @Getter
    private final UUID requestId = UUID.randomUUID();

    @Getter
    private final UUID clientRequestId;

    @Getter
    private final Long userId;

    @Getter
    private final Jwt principal;

    // Câu hỏi và ngữ cảnh hội thoại
    @Getter
    private final String question;

    @Getter
    private final ChatHistoryService.ConversationContext conversation;

    // Cấu hình và các dependency hỗ trợ
    private final AiAdvisorProperties properties;
    private final AiTokenCounter tokenCounter;
    private final Clock clock;

    // Thời gian bắt đầu và thời hạn xử lý request
    private final Instant startedInstant;

    @Getter
    private final Instant deadline;

    // Theo dõi tool call trùng lặp và lưu dữ liệu truy xuất
    private final Map<String, Integer> identicalToolCalls = new HashMap<>();
    private final Map<Long, RetrievedChunk> retrievedChunksById = new LinkedHashMap<>();

    // Quản lý các tác vụ được lên lịch
    private final List<ScheduledFuture<?>> scheduledTasks = new ArrayList<>();

    // Quản lý vòng đời, trạng thái kết thúc và lỗi của request
    private AdvisorRequestState state = AdvisorRequestState.RUNNING;
    private LocalDateTime finishedAt;
    private String stopReason;
    private Integer errorCode;

    // Theo dõi thứ tự sự kiện, số lần gọi model, tool và ngân sách token
    private long sequence;
    private int modelCalls;
    private int toolCalls;
    private int reservedTokens;

    // Lưu hồ sơ học tập và phản hồi của AI
    private AdvisorAcademicProfileResponse academicProfile;
    private GpaGoalResponse gpaGoal;
    private GpaGoalResponse gpaContext;
    private boolean gpaGoalAttempted;
    private PrerequisiteImpactResponse prerequisiteImpact;
    private PrerequisiteImpactResponse prerequisiteContext;
    private boolean prerequisiteAnalysisAttempted;
    private MinimumExamScoreResponse examScore;
    private MinimumExamScoreResponse examScoreContext;
    private boolean examScoreCalculationAttempted;
    private ChatResponse response;

    // Quản lý SSE streaming và trạng thái kết nối với client
    private EventWriter eventWriter;
    private Runnable completeOutput;
    private boolean disconnected;

    // Quản lý luồng thực thi và trạng thái lên lịch của worker
    private Thread workerThread;
    private boolean workerScheduled;

    public AdvisorChatExecution(
            UUID clientRequestId,
            Long userId,
            Jwt principal,
            String question,
            ChatHistoryService.ConversationContext conversation,
            AiAdvisorProperties properties,
            AiTokenCounter tokenCounter,
            Clock clock) {
        this.clientRequestId = clientRequestId;
        this.userId = userId;
        this.principal = principal;
        this.question = question;
        this.conversation = conversation;
        this.properties = properties;
        this.tokenCounter = tokenCounter;
        this.clock = clock;
        this.startedInstant = clock.instant();
        this.deadline = startedInstant.plus(properties.getExecutionTimeout());
    }

    public long getRemainingMillis() {
        return Math.max(1L, Duration.between(clock.instant(), deadline).toMillis());
    }

    public synchronized boolean isRunning() {
        return state == AdvisorRequestState.RUNNING;
    }

    public synchronized boolean shouldOfferTools() {
        return modelCalls + 1 < properties.getMaximumModelCalls();
    }

    public int getMaximumOutputTokens() {
        return properties.getMaximumOutputTokens();
    }

    public synchronized AdvisorAcademicProfileResponse getAcademicProfile() {
        return academicProfile;
    }

    public synchronized GpaGoalResponse getGpaGoal() {
        return gpaGoal;
    }

    public synchronized GpaGoalResponse getGpaContext() {
        return gpaContext == null ? conversation.latestGpaGoal() : gpaContext;
    }

    public synchronized void beginGpaGoalSearch() {
        checkCanContinue();
        gpaGoalAttempted = true;
        // Lần tìm mới lỗi không được phát phương án thành công của lần gọi trước như kết quả yêu cầu mới.
        gpaGoal = null;
    }

    public synchronized boolean hasAttemptedGpaGoalSearch() {
        return gpaGoalAttempted;
    }

    public synchronized PrerequisiteImpactResponse getPrerequisiteImpact() {
        return prerequisiteImpact;
    }

    public synchronized PrerequisiteImpactResponse getPrerequisiteContext() {
        return prerequisiteContext == null ? conversation.latestPrerequisiteImpact() : prerequisiteContext;
    }

    public synchronized void beginPrerequisiteAnalysis() {
        checkCanContinue();
        prerequisiteAnalysisAttempted = true;
        prerequisiteImpact = null;
    }

    public synchronized boolean hasAttemptedPrerequisiteAnalysis() {
        return prerequisiteAnalysisAttempted;
    }

    public synchronized void recordPrerequisiteImpact(PrerequisiteImpactResponse response) {
        checkCanContinue();
        prerequisiteImpact = response;
        prerequisiteContext = response;
    }

    public synchronized MinimumExamScoreResponse getExamScore() {
        return examScore;
    }

    public synchronized MinimumExamScoreResponse getExamScoreContext() {
        return examScoreContext == null ? conversation.latestExamScore() : examScoreContext;
    }

    public synchronized void beginExamScoreCalculation() {
        checkCanContinue();
        examScoreCalculationAttempted = true;
        examScore = null;
    }

    public synchronized boolean hasAttemptedExamScoreCalculation() {
        return examScoreCalculationAttempted;
    }

    public synchronized void recordExamScore(MinimumExamScoreResponse response) {
        checkCanContinue();
        examScore = response;
        examScoreContext = response;
    }

    public synchronized List<RetrievedChunk> getRetrievedChunks() {
        return List.copyOf(retrievedChunksById.values());
    }

    public synchronized void connect(EventWriter writer, Runnable outputCompletion) {
        if (eventWriter != null) throw new IllegalStateException("Lượt chat đã có kết nối");
        eventWriter = writer;
        completeOutput = outputCompletion;
    }

    /**
     * Đăng ký thread hiện tại làm worker của lượt chat này, để cancel/limit có thể interrupt khi cần.
     * Phải gọi từ chính worker thread (không phải thread Tomcat) và kiểm tra ngay trạng thái,
     * vì lượt chat có thể đã bị hủy hoặc quá hạn trong lúc chờ trong hàng đợi của executor.
     */
    public synchronized void registerWorkerThread() {
        workerThread = Thread.currentThread();
        checkCanContinue();
    }

    public synchronized boolean tryScheduleWorker() {
        if (workerScheduled) return false;
        workerScheduled = true;
        return true;
    }

    public synchronized void addScheduledTask(ScheduledFuture<?> task) {
        if (state == AdvisorRequestState.RUNNING) scheduledTasks.add(task);
        else task.cancel(false);
    }

    public synchronized void checkCanContinue() {
        if (state != AdvisorRequestState.RUNNING) throw new AdvisorExecutionStoppedException();
        if (!clock.instant().isBefore(deadline)) {
            limit("DEADLINE");
            throw new AdvisorExecutionStoppedException();
        }
        if (Thread.currentThread().isInterrupted()) {
            cancel("INTERRUPTED");
            throw new AdvisorExecutionStoppedException();
        }
    }

    public synchronized void beforeModelCall(String serializedContext) {
        checkCanContinue();
        int estimatedInputTokens = tokenCounter.countTokens(serializedContext) + 512;
        long reservation = (long) estimatedInputTokens + properties.getMaximumOutputTokens();
        if (modelCalls >= properties.getMaximumModelCalls()
                || estimatedInputTokens > properties.getMaximumContextTokens()
                || (long) reservedTokens + reservation > properties.getMaximumTotalTokens()) {
            limit("MODEL_OR_TOKEN_BUDGET");
            throw new AdvisorExecutionStoppedException();
        }
        // Dự phòng toàn bộ output mỗi vòng; không hoàn lại theo usage thiếu để tránh tăng ngân sách ngầm.
        modelCalls++;
        reservedTokens += (int) reservation;
    }

    public synchronized void startTool(String toolCallId, String toolName, String canonicalArguments, String label) {
        checkCanContinue();
        String signature = toolName + ":" + canonicalArguments;
        int identicalCount = identicalToolCalls.getOrDefault(signature, 0);
        if (toolCalls >= properties.getMaximumToolCalls()
                || identicalCount >= properties.getMaximumIdenticalToolCalls()) {
            limit("TOOL_BUDGET");
            throw new AdvisorExecutionStoppedException();
        }
        toolCalls++;
        identicalToolCalls.put(signature, identicalCount + 1);
        publish("tool.started", Map.of("toolCallId", toolCallId, "toolName", toolName, "displayMessage", label));
    }

    public synchronized void completeTool(String toolCallId, String toolName) {
        publish("tool.completed", Map.of("toolCallId", toolCallId, "toolName", toolName));
    }

    public synchronized void failTool(String toolCallId, String toolName, int code) {
        publish(
                "tool.failed",
                Map.of(
                        "toolCallId",
                        toolCallId,
                        "toolName",
                        toolName,
                        "code",
                        code,
                        "displayMessage",
                        "Không thể lấy dữ liệu cho công cụ này."));
    }

    public synchronized void recordAcademicProfile(AdvisorAcademicProfileResponse profile) {
        checkCanContinue();
        academicProfile = profile;
    }

    public synchronized void recordGpaGoal(GpaGoalResponse goal) {
        checkCanContinue();
        gpaGoal = goal;
        gpaContext = goal;
    }

    public synchronized void recordKnowledgeChunks(List<RetrievedChunk> chunks) {
        checkCanContinue();
        chunks.forEach(chunk -> retrievedChunksById.put(chunk.chunkId(), chunk));
    }

    public synchronized void publish(String eventName, Object payload) {
        checkCanContinue();
        writeEvent(eventName, payload);
        checkCanContinue();
    }

    public synchronized void heartbeat() {
        if (isRunning()) writeEvent("agent.heartbeat", Map.of());
    }

    public synchronized void complete(Supplier<ChatResponse> saveExchange) {
        checkCanContinue();
        // Nếu chốt lưu đã bắt đầu, yêu cầu hủy chờ cùng khóa và nhận trạng thái cuối;
        // nếu hủy thắng trước đó, supplier không được gọi và không có lượt trả lời thành công muộn.
        response = saveExchange.get();
        state = AdvisorRequestState.COMPLETED;
        finishedAt = LocalDateTime.now(clock);
        stopReason = "COMPLETED";
        cancelScheduledTasks();
        if (academicProfile != null) {
            writeEvent(
                    "ui.component", Map.of("type", "academic_profile", "schemaVersion", 1, "payload", academicProfile));
            writeEvent("context.updated", Map.of("academicProfile", academicProfile));
        }
        // Phát đúng payload đã commit, cùng cấu trúc với lịch sử và response trong GET status.
        if (response.getPayload() != null && response.getPayload().gpaGoal() != null) {
            var savedGoal = response.getPayload().gpaGoal();
            writeEvent("ui.component", Map.of("type", "gpa_goal", "schemaVersion", 1, "payload", savedGoal));
            writeEvent("context.updated", Map.of("gpaGoal", savedGoal));
        }
        if (response.getPayload() != null && response.getPayload().prerequisiteImpact() != null) {
            var savedPrerequisites = response.getPayload().prerequisiteImpact();
            writeEvent(
                    "ui.component",
                    Map.of("type", "prerequisite_impact", "schemaVersion", 1, "payload", savedPrerequisites));
            writeEvent("context.updated", Map.of("prerequisiteImpact", savedPrerequisites));
        }
        if (response.getPayload() != null && response.getPayload().examScore() != null) {
            var savedExamScore = response.getPayload().examScore();
            writeEvent("ui.component", Map.of("type", "exam_score", "schemaVersion", 1, "payload", savedExamScore));
            writeEvent("context.updated", Map.of("examScore", savedExamScore));
        }
        if (!response.getCitations().isEmpty())
            writeEvent(
                    "ui.component",
                    Map.of("type", "knowledge_sources", "schemaVersion", 1, "payload", response.getCitations()));
        // Buffer câu trả lời để hậu kiểm và kiểm tra lại nguồn trước khi phát, không giả lập tốc độ gõ.
        String content = response.getContent();
        for (int offset = 0; offset < content.length(); ) {
            int end =
                    content.offsetByCodePoints(offset, Math.min(256, content.codePointCount(offset, content.length())));
            writeEvent("text.delta", Map.of("delta", content.substring(offset, end)));
            offset = end;
        }
        writeEvent("agent.completed", response);
        closeOutput();
    }

    public synchronized void cancel(String reason) {
        finish(AdvisorRequestState.CANCELLED, "agent.cancelled", reason, null);
    }

    public synchronized void limit(String reason) {
        finish(AdvisorRequestState.LIMIT_REACHED, "agent.limit_reached", reason, null);
    }

    public synchronized void fail(int code) {
        finish(AdvisorRequestState.FAILED, "agent.error", "ERROR", code);
    }

    public synchronized void disconnect() {
        disconnected = true;
        cancel("DISCONNECTED");
    }

    private void finish(AdvisorRequestState terminalState, String eventName, String reason, Integer code) {
        if (!isRunning()) return;
        state = terminalState;
        stopReason = reason;
        errorCode = code;
        finishedAt = LocalDateTime.now(clock);
        cancelScheduledTasks();
        writeEvent(
                eventName,
                code == null
                        ? Map.of("reason", reason)
                        : Map.of("reason", reason, "code", code, "message", "Không thể hoàn tất lượt trợ lý này."));
        closeOutput();
        if (workerThread != null && workerThread != Thread.currentThread()) workerThread.interrupt();
    }

    private void writeEvent(String eventName, Object payload) {
        if (eventWriter == null || disconnected) return;
        String sessionId = response != null
                ? response.getSessionId()
                : conversation.sessionId() == null
                        ? null
                        : conversation.sessionId().toString();
        try {
            eventWriter.write(eventName, new AdvisorEventResponse(requestId, sessionId, ++sequence, payload));
        } catch (IOException | IllegalStateException exception) {
            // Servlet xử lý lỗi ghi kết nối; không phát thêm terminal lên kết nối đã mất.
            disconnected = true;
            cancel("DISCONNECTED");
        }
    }

    private void closeOutput() {
        if (completeOutput != null && !disconnected) completeOutput.run();
    }

    private void cancelScheduledTasks() {
        scheduledTasks.forEach(task -> task.cancel(false));
        scheduledTasks.clear();
    }

    public synchronized AdvisorRequestStatusResponse getStatus() {
        String sessionId = response != null
                ? response.getSessionId()
                : conversation.sessionId() == null
                        ? null
                        : conversation.sessionId().toString();
        return new AdvisorRequestStatusResponse(
                requestId,
                clientRequestId,
                sessionId,
                state,
                LocalDateTime.ofInstant(startedInstant, clock.getZone()),
                finishedAt,
                sequence,
                modelCalls,
                toolCalls,
                reservedTokens,
                stopReason,
                errorCode,
                response,
                academicProfile);
    }

    @FunctionalInterface
    public interface EventWriter {
        void write(String eventName, AdvisorEventResponse event) throws IOException;
    }

    /** Tín hiệu điều khiển nội bộ, không chuyển stack trace hoặc nguyên nhân provider sang SSE. */
    public static final class AdvisorExecutionStoppedException extends RuntimeException {
        public AdvisorExecutionStoppedException() {
            super(null, null, false, false);
        }
    }
}
