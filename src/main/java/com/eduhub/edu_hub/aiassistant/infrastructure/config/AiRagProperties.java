package com.eduhub.edu_hub.aiassistant.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@Getter
@Setter
@ConfigurationProperties(prefix = "eduhub.ai.rag")
public class AiRagProperties {
    private int topK = 3;
    private double similarityThreshold = 0.70;
    private int memoryMessageLimit = 20;
    private int chunkSize = 800;
    private DataSize maxFileSize = DataSize.ofMegabytes(20);
    private String fallbackMessage;
    private String systemPrompt;
}
