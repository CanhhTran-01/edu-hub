package com.eduhub.edu_hub.shared.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Cung cấp đồng hồ chung để xác định ngày nghiệp vụ theo múi giờ Việt Nam. */
@Configuration
public class TimeConfiguration {
    /** Timestamp lưu theo UTC vẫn cần quy đổi rõ ràng từ instant của đồng hồ này. */
    @Bean
    public Clock applicationClock() {
        return Clock.system(ZoneId.of("Asia/Ho_Chi_Minh"));
    }
}
