package com.eduhub.edu_hub.aiassistant.api.dto.request;

import com.eduhub.edu_hub.aiassistant.domain.enums.KnowledgeSourceStatus;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class SearchKnowledgeSourcesRequest {
    @Size(max = 255)
    private String keyword;

    private KnowledgeSourceStatus status;

    @Min(0)
    private int page = 0;

    @Min(1)
    @Max(100)
    private int size = 20;
}
