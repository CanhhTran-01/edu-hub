package com.eduhub.edu_hub.aiassistant.application.service;

import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiRagProperties;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Giới hạn số lượt hỏi theo User ID trên một instance. */
@Service
@RequiredArgsConstructor
public class AiQuestionRateLimiter {
    private static final int MAXIMUM_TRACKED_USERS = 100000;
    private final AiRagProperties properties;
    private final Clock clock;
    private final Map<Long, RateWindow> rateWindows = new ConcurrentHashMap<>();

    public void consumeQuestionAllowance(Long userId) {
        Instant now = clock.instant();
        rateWindows
                .entrySet()
                .removeIf(entry -> !entry.getValue().startedAt().plusSeconds(60).isAfter(now));
        if (!rateWindows.containsKey(userId) && rateWindows.size() >= MAXIMUM_TRACKED_USERS)
            throw new AiAssistantException(ErrorCode.AI_RATE_LIMIT_EXCEEDED);
        rateWindows.compute(userId, (accountId, previousWindow) -> {
            RateWindow window = previousWindow == null
                            || !previousWindow.startedAt().plusSeconds(60).isAfter(now)
                    ? new RateWindow(now, 0)
                    : previousWindow;
            if (window.questionCount() >= properties.getQuestionsPerMinute())
                throw new AiAssistantException(ErrorCode.AI_RATE_LIMIT_EXCEEDED);
            return new RateWindow(window.startedAt(), window.questionCount() + 1);
        });
    }

    private record RateWindow(Instant startedAt, int questionCount) {}
}
