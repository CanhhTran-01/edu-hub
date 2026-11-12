package com.eduhub.edu_hub.scheduling.infrastructure.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "scheduling.timetabling")
public class TimetablingSettings {
    @Min(1)
    private long spentLimitSeconds = 60; // thời gian tối đa cho mỗi lần giả

    @Min(1)
    private int maximumSessionsPerPlan = 10000; // Giới hạn tài nguyên cho một tác vụ.
}
