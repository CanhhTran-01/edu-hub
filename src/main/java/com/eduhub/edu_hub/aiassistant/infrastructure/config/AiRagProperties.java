package com.eduhub.edu_hub.aiassistant.infrastructure.config;

import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantConfigurationException;
import jakarta.validation.constraints.*;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@ConfigurationProperties(prefix = "eduhub.ai.rag")
@Validated
public class AiRagProperties {
    @Min(1)
    @Max(100)
    private int topK = 3;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double similarityThreshold = 0.70;

    @Min(1)
    @Max(100)
    private int memoryMessageLimit = 20;

    @Min(100)
    @Max(2000)
    private int chunkSize = 800;

    private DataSize maxFileSize = DataSize.ofMegabytes(20);

    @NotBlank
    private String fallbackMessage;

    @NotBlank
    private String systemPrompt;

    /** Chặn cấu hình thiếu tại ranh giới gọi model, không gửi yêu cầu trả lời thiếu chỉ dẫn hệ thống. */
    public String requireSystemPrompt() {
        if (!StringUtils.hasText(systemPrompt)) {
            throw new AiAssistantConfigurationException("Thiếu hoặc trống cấu hình eduhub.ai.rag.system-prompt");
        }
        return systemPrompt;
    }

    @NotBlank
    private String embeddingModel = "gemini-embedding-001";

    @NotBlank
    private String embeddingModelVersion = "001";

    @Min(1)
    @Max(20)
    private int maximumContextChunks = 5;

    @Min(100)
    private int contextTokenLimit = 4000;

    @Min(100)
    private int memoryTokenLimit = 2000;

    @Min(1000)
    private int promptTokenLimit = 10000;

    @Min(1)
    private int questionsPerMinute = 10;

    @Min(1)
    private int maximumExtractedCharacters = 2000000;

    @Min(1)
    private int maximumChunks = 2000;

    @Min(1)
    @Max(100)
    private int embeddingBatchSize = 32;

    @NotNull
    private Duration requestTimeout = Duration.ofSeconds(30);

    @NotNull
    private Duration ingestionTimeout = Duration.ofMinutes(10);

    @AssertTrue(
            message =
                    "Thời hạn AI phải dương, dung lượng tệp phải từ 1 byte đến 20 MB và ngân sách ngữ cảnh phải phù hợp")
    public boolean isResourceConfigurationValid() {
        return requestTimeout != null
                && !requestTimeout.isNegative()
                && !requestTimeout.isZero()
                && requestTimeout.toMillis() >= 1
                && requestTimeout.compareTo(Duration.ofMinutes(2)) <= 0
                && ingestionTimeout != null
                && !ingestionTimeout.isNegative()
                && !ingestionTimeout.isZero()
                && ingestionTimeout.toMillis() >= 1
                && maxFileSize != null
                && maxFileSize.toBytes() > 0
                && maxFileSize.toBytes() <= DataSize.ofMegabytes(20).toBytes()
                && contextTokenLimit + memoryTokenLimit < promptTokenLimit;
    }
}
