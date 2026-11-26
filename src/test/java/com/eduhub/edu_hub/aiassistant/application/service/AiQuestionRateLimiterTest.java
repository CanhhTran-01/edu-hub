package com.eduhub.edu_hub.aiassistant.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiRagProperties;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.*;
import org.junit.jupiter.api.Test;

class AiQuestionRateLimiterTest {
    @Test
    void configuredQuotaIsEnforcedSeparatelyForEachStudent() {
        var properties = new AiRagProperties();
        properties.setQuestionsPerMinute(2);
        var clock = Clock.fixed(Instant.parse("2026-11-25T17:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        var rateLimiter = new AiQuestionRateLimiter(properties, clock);

        rateLimiter.consumeQuestionAllowance(7L);
        rateLimiter.consumeQuestionAllowance(7L);

        assertThatThrownBy(() -> rateLimiter.consumeQuestionAllowance(7L))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.AI_RATE_LIMIT_EXCEEDED));
        assertThatCode(() -> rateLimiter.consumeQuestionAllowance(8L)).doesNotThrowAnyException();
    }

    @Test
    void quotaIsRestoredAtTheExactEndOfTheOneMinuteWindow() {
        var properties = new AiRagProperties();
        properties.setQuestionsPerMinute(1);
        var clock = mock(Clock.class);
        var startedAt = Instant.parse("2026-11-25T17:00:00Z");
        when(clock.instant()).thenReturn(startedAt);
        var rateLimiter = new AiQuestionRateLimiter(properties, clock);
        rateLimiter.consumeQuestionAllowance(7L);

        when(clock.instant()).thenReturn(startedAt.plusSeconds(59));
        assertThatThrownBy(() -> rateLimiter.consumeQuestionAllowance(7L))
                .isInstanceOfSatisfying(AiAssistantException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.AI_RATE_LIMIT_EXCEEDED));

        when(clock.instant()).thenReturn(startedAt.plusSeconds(60));
        assertThatCode(() -> rateLimiter.consumeQuestionAllowance(7L)).doesNotThrowAnyException();
    }
}
