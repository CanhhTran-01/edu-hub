package com.eduhub.edu_hub.identity.infrastructure.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.identity.application.service.RefreshTokenCleanupService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

class RefreshTokenCleanupSchedulingConfigurationTest {
    @Test
    void defaultsToTwoAmInVietnamAndRunsCleanupOnTheDedicatedScheduler() throws Exception {
        var scheduled = RefreshTokenCleanupSchedulingConfiguration.class
                .getMethod("deleteExpiredRefreshTokens")
                .getAnnotation(Scheduled.class);
        String defaultCron = scheduled
                .cron()
                .substring(scheduled.cron().indexOf(':') + 1, scheduled.cron().length() - 1);
        var nextRun = CronExpression.parse(defaultCron)
                .next(LocalDateTime.of(2026, 11, 24, 2, 0).atZone(ZoneId.of(scheduled.zone())));
        assertThat(nextRun).isEqualTo(LocalDateTime.of(2026, 11, 25, 2, 0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")));
        assertThat(scheduled.scheduler()).isEqualTo("refreshTokenCleanupTaskScheduler");
        var cleanupService = mock(RefreshTokenCleanupService.class);
        new RefreshTokenCleanupSchedulingConfiguration(cleanupService).deleteExpiredRefreshTokens();
        verify(cleanupService).deleteExpiredRefreshTokens();
    }
}
