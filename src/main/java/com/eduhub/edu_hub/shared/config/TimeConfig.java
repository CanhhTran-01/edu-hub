package com.eduhub.edu_hub.shared.config;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;

/** Cung cấp đồng hồ chung để xác định ngày nghiệp vụ theo múi giờ Việt Nam. */
@Configuration
public class TimeConfig {
    public static final ZoneId APPLICATION_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Ngày/giờ nghiệp vụ theo Việt Nam; metadata audit có provider UTC riêng ở bên dưới. */
    @Bean
    public Clock applicationClock() {
        return Clock.system(APPLICATION_ZONE);
    }

    /** Audit dùng cùng thời điểm với nghiệp vụ nhưng tiếp tục lưu LocalDateTime theo UTC. */
    @Bean
    public DateTimeProvider applicationDateTimeProvider(Clock clock) {
        return () -> Optional.of(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }

    /** Validation ngày sinh dùng cùng ngày Việt Nam với kiểm tra nghiệp vụ trong service. */
    @Bean
    public ValidationConfigurationCustomizer validationClockCustomizer(Clock clock) {
        return configuration -> configuration.clockProvider(() -> clock);
    }
}
