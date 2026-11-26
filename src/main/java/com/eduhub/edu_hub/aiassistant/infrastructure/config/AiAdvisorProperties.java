package com.eduhub.edu_hub.aiassistant.infrastructure.config;

import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantConfigurationException;
import jakarta.validation.constraints.*;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Ngân sách tài nguyên của lát cắt Agent, độc lập quy chế học vụ và cấu hình embedding. */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "eduhub.ai.advisor")
public class AiAdvisorProperties {
    @NotBlank
    private String systemPrompt;

    public String requireSystemPrompt() {
        if (systemPrompt == null || systemPrompt.isBlank())
            throw new AiAssistantConfigurationException("Thiếu hoặc trống cấu hình eduhub.ai.advisor.system-prompt");
        return systemPrompt;
    }

    @Min(2)
    @Max(10)
    private int maximumModelCalls = 5;

    @Min(1)
    @Max(20)
    private int maximumToolCalls = 8;

    @Min(1)
    @Max(3)
    private int maximumIdenticalToolCalls = 2;

    @Min(1)
    @Max(8)
    private int maximumConcurrentRequests = 4;

    @Min(1)
    @Max(20)
    private int recentMessages = 10;

    @Min(1000)
    private int maximumContextTokens = 12000;

    @Min(1000)
    private int maximumTotalTokens = 30000;

    @Min(100)
    @Max(4000)
    private int maximumOutputTokens = 2000;

    @Min(8)
    @Max(10000)
    private int maximumTrackedRequests = 1000;

    private Duration executionTimeout = Duration.ofSeconds(60);
    private Duration requestRetention = Duration.ofMinutes(10);

    @AssertTrue(message = "Ngân sách Advisor, thời hạn và dung lượng lưu trạng thái phải hợp lệ")
    public boolean isResourceConfigurationValid() {
        // NOTE: Giữ kiểm tra chéo khi method được gọi trước Bean Validation hoặc biên @Min/@Max thay đổi.
        return executionTimeout != null
                && executionTimeout.compareTo(Duration.ofSeconds(1)) >= 0
                && executionTimeout.compareTo(Duration.ofMinutes(2)) <= 0
                && requestRetention != null
                && requestRetention.compareTo(Duration.ofMinutes(1)) >= 0
                && requestRetention.compareTo(Duration.ofHours(1)) <= 0
                && maximumTrackedRequests >= maximumConcurrentRequests
                && (long) maximumContextTokens + maximumOutputTokens <= maximumTotalTokens;
    }
}
