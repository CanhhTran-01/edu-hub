package com.eduhub.edu_hub.notification.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduhub.edu_hub.notification.domain.enums.NotificationStatus;
import com.eduhub.edu_hub.notification.domain.model.QueuedAdmissionNotification;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NotificationRetryPolicyTest {
    private static final LocalDateTime ATTEMPT_TIME = LocalDateTime.of(2026, 11, 8, 9, 0);

    @ParameterizedTest
    @CsvSource({
        "PENDING, 0, true, true",
        "FAILED, 1, false, true",
        "FAILED, 3, false, true",
        "FAILED, 4, false, false",
        "SENT, 0, false, false",
        "SENT, 3, false, false",
        "SKIPPED, 0, false, false"
    })
    void distinguishesPostCommitKickoffFromScheduledAttempts(
            NotificationStatus status, int failureCount, boolean initialAllowed, boolean scheduledAllowed) {
        var notification = QueuedAdmissionNotification.builder()
                .state(status)
                .failureCount(failureCount)
                .nextAttemptAt(ATTEMPT_TIME)
                .build();

        assertThat(NotificationRetryPolicy.allowsInitialAttempt(notification, ATTEMPT_TIME))
                .isEqualTo(initialAllowed);
        assertThat(NotificationRetryPolicy.allowsScheduledAttempt(notification, ATTEMPT_TIME))
                .isEqualTo(scheduledAllowed);
    }

    @Test
    void neitherPathSendsBeforeTheDueTime() {
        var notification = QueuedAdmissionNotification.builder()
                .state(NotificationStatus.PENDING)
                .nextAttemptAt(ATTEMPT_TIME.plusNanos(1))
                .build();

        assertThat(NotificationRetryPolicy.allowsInitialAttempt(notification, ATTEMPT_TIME))
                .isFalse();
        assertThat(NotificationRetryPolicy.allowsScheduledAttempt(notification, ATTEMPT_TIME))
                .isFalse();
    }

    @Test
    void missingNotificationIsIgnoredByBothPaths() {
        assertThat(NotificationRetryPolicy.allowsInitialAttempt(null, ATTEMPT_TIME))
                .isFalse();
        assertThat(NotificationRetryPolicy.allowsScheduledAttempt(null, ATTEMPT_TIME))
                .isFalse();
    }
}
