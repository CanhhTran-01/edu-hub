package com.eduhub.edu_hub.aiassistant.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.aiassistant.api.dto.response.*;
import com.eduhub.edu_hub.aiassistant.application.service.AdvisorChatExecution.AdvisorExecutionStoppedException;
import com.eduhub.edu_hub.aiassistant.domain.enums.AdvisorRequestState;
import com.eduhub.edu_hub.aiassistant.infrastructure.adapter.AiTokenCounter;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiAdvisorProperties;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AdvisorChatExecutionTest {
    AiAdvisorProperties properties = new AiAdvisorProperties();
    Clock clock = Clock.fixed(Instant.parse("2026-11-25T17:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    AiTokenCounter counter = mock(AiTokenCounter.class);
    List<String> eventNames = new ArrayList<>();
    List<AdvisorEventResponse> events = new ArrayList<>();

    AdvisorChatExecution execution() {
        var execution = new AdvisorChatExecution(
                UUID.randomUUID(),
                7L,
                Jwt.withTokenValue("test")
                        .header("alg", "HS256")
                        .subject("student")
                        .build(),
                "Học vụ?",
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
        return execution;
    }

    @Test
    void cancellationPreventsLateToolResultsAndHistoryCommitAndHasOnlyOneTerminal() {
        var execution = execution();
        execution.startTool("t1", "getAcademicProfile", "{}", "Đang đọc");
        execution.cancel("USER_REQUEST");
        var saved = new AtomicBoolean();
        assertThatThrownBy(() -> execution.complete(() -> {
                    saved.set(true);
                    return null;
                }))
                .isInstanceOf(AdvisorExecutionStoppedException.class);
        assertThatThrownBy(() -> execution.completeTool("t1", "getAcademicProfile"))
                .isInstanceOf(AdvisorExecutionStoppedException.class);
        execution.fail(4605);
        execution.limit("DEADLINE");
        execution.heartbeat();
        assertThat(saved).isFalse();
        assertThat(execution.getStatus().state()).isEqualTo(AdvisorRequestState.CANCELLED);
        assertThat(eventNames).containsExactly("tool.started", "agent.cancelled");
        assertThat(events).extracting(AdvisorEventResponse::sequence).containsExactly(1L, 2L);
    }

    @Test
    void identicalToolBudgetStopsBeforeThirdToolStarts() {
        var execution = execution();
        execution.startTool("t1", "getAcademicProfile", "{}", "Đang đọc");
        execution.completeTool("t1", "getAcademicProfile");
        execution.startTool("t2", "getAcademicProfile", "{}", "Đang đọc");
        execution.completeTool("t2", "getAcademicProfile");
        assertThatThrownBy(() -> execution.startTool("t3", "getAcademicProfile", "{}", "Đang đọc"))
                .isInstanceOf(AdvisorExecutionStoppedException.class);
        assertThat(execution.getStatus().toolCalls()).isEqualTo(2);
        assertThat(eventNames)
                .containsExactly(
                        "tool.started", "tool.completed", "tool.started", "tool.completed", "agent.limit_reached");
    }

    @Test
    void tokenPreflightStopsBeforeModelCallIsCharged() {
        when(counter.countTokens("context")).thenReturn(properties.getMaximumContextTokens());
        var execution = execution();
        assertThatThrownBy(() -> execution.beforeModelCall("context"))
                .isInstanceOf(AdvisorExecutionStoppedException.class);
        assertThat(execution.getStatus().modelCalls()).isZero();
        assertThat(execution.getStatus().state()).isEqualTo(AdvisorRequestState.LIMIT_REACHED);
        assertThat(eventNames).containsExactly("agent.limit_reached");
    }

    @Test
    void modelBudgetIncludesLastResponseAndDoesNotAllowAnExtraCall() {
        properties.setMaximumModelCalls(2);
        var execution = execution();
        assertThat(execution.shouldOfferTools()).isTrue();
        execution.beforeModelCall("first");
        assertThat(execution.shouldOfferTools()).isFalse();
        execution.beforeModelCall("last");
        assertThatThrownBy(() -> execution.beforeModelCall("extra"))
                .isInstanceOf(AdvisorExecutionStoppedException.class);
        assertThat(execution.getStatus().modelCalls()).isEqualTo(2);
    }

    @Test
    void totalTokenBudgetReservesOutputAcrossCallsBeforeAcceptingAnotherCall() {
        properties.setMaximumContextTokens(2000);
        properties.setMaximumTotalTokens(5000);
        var execution = execution();
        execution.beforeModelCall("first");
        assertThatThrownBy(() -> execution.beforeModelCall("second"))
                .isInstanceOf(AdvisorExecutionStoppedException.class);
        assertThat(execution.getStatus().modelCalls()).isEqualTo(1);
        assertThat(execution.getStatus().reservedTokens()).isEqualTo(2512);
        assertThat(execution.getStatus().state()).isEqualTo(AdvisorRequestState.LIMIT_REACHED);
    }

    @Test
    void deadlineStopsBeforeStartingAnotherModelCall() {
        var advancingClock = mock(Clock.class);
        when(advancingClock.getZone()).thenReturn(clock.getZone());
        when(advancingClock.instant())
                .thenReturn(clock.instant(), clock.instant().plusSeconds(60));
        var execution = new AdvisorChatExecution(
                UUID.randomUUID(),
                7L,
                execution().getPrincipal(),
                "GPA?",
                new ChatHistoryService.ConversationContext(null, null, List.of()),
                properties,
                counter,
                advancingClock);
        execution.connect((name, event) -> eventNames.add(name), () -> {});
        assertThatThrownBy(() -> execution.beforeModelCall("context"))
                .isInstanceOf(AdvisorExecutionStoppedException.class);
        assertThat(execution.getStatus().modelCalls()).isZero();
        assertThat(execution.getStatus().stopReason()).isEqualTo("DEADLINE");
        assertThat(eventNames).containsExactly("agent.limit_reached");
    }

    @Test
    void toolBudgetCountsDifferentArgumentsAndFailureCodeRemainsAvailableInStatus() {
        properties.setMaximumToolCalls(2);
        var execution = execution();
        execution.startTool("t1", "searchAcademicKnowledge", "first query", "Đang tra cứu");
        execution.failTool("t1", "searchAcademicKnowledge", 4605);
        execution.startTool("t2", "searchAcademicKnowledge", "second query", "Đang tra cứu");
        assertThatThrownBy(() -> execution.startTool("t3", "getAcademicProfile", "{}", "Đang đọc"))
                .isInstanceOf(AdvisorExecutionStoppedException.class);
        assertThat(execution.getStatus().toolCalls()).isEqualTo(2);
        assertThat(eventNames).endsWith("agent.limit_reached");
        var failed = execution();
        failed.fail(4605);
        assertThat(failed.getStatus().errorCode()).isEqualTo(4605);
    }

    @Test
    void failedConnectionCancelsWithoutSavingOrEmittingMoreEvents() {
        var execution = execution();
        var failingExecution = new AdvisorChatExecution(
                UUID.randomUUID(),
                7L,
                execution.getPrincipal(),
                "Học vụ?",
                execution.getConversation(),
                properties,
                counter,
                clock);
        failingExecution.connect(
                (name, envelope) -> {
                    throw new IOException("Ngắt kết nối thử nghiệm");
                },
                () -> {});
        assertThatThrownBy(() -> failingExecution.publish("agent.started", Map.of()))
                .isInstanceOf(AdvisorExecutionStoppedException.class);
        assertThat(failingExecution.getStatus().state()).isEqualTo(AdvisorRequestState.CANCELLED);
    }

    @Test
    void completedTextKeepsUnicodeAndSessionIdAndIgnoresLateCancellation() {
        var execution = execution();
        String content = "Nội dung học vụ 🎓 ".repeat(60);
        execution.complete(() -> ChatResponse.builder()
                .sessionId("3")
                .messageId("11")
                .content(content)
                .citations(List.of())
                .build());
        execution.cancel("LATE_CANCEL");
        assertThat(execution.getStatus().state()).isEqualTo(AdvisorRequestState.COMPLETED);
        assertThat(events).allSatisfy(event -> assertThat(event.sessionId()).isEqualTo("3"));
        String delivered = events.stream()
                .filter(event -> event.data() instanceof Map<?, ?> payload && payload.containsKey("delta"))
                .map(event -> (String) ((Map<?, ?>) event.data()).get("delta"))
                .reduce("", String::concat);
        assertThat(delivered).isEqualTo(content);
        assertThat(eventNames.getLast()).isEqualTo("agent.completed");
    }
}
