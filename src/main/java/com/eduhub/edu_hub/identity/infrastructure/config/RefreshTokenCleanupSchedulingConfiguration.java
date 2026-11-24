package com.eduhub.edu_hub.identity.infrastructure.config;

import com.eduhub.edu_hub.identity.application.service.RefreshTokenCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Lập lịch dọn token riêng để không giữ luồng quét thông báo hoặc vòng đời lớp học phần. */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanupSchedulingConfiguration {
    private final RefreshTokenCleanupService refreshTokenCleanupService;

    @Scheduled(
            cron = "${identity.refresh-token-cleanup.cron:0 0 2 * * *}",
            zone = "Asia/Ho_Chi_Minh",
            scheduler = "refreshTokenCleanupTaskScheduler")
    public void deleteExpiredRefreshTokens() {
        long deletedTokenCount = refreshTokenCleanupService.deleteExpiredRefreshTokens();
        log.info("Đã dọn refresh token hết hạn: deletedTokenCount={}", deletedTokenCount);
    }

    @Bean(defaultCandidate = false)
    ThreadPoolTaskScheduler refreshTokenCleanupTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("refresh-token-cleanup-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }
}
