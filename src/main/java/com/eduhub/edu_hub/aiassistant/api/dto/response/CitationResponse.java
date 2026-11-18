package com.eduhub.edu_hub.aiassistant.api.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CitationResponse {
    private String referenceCode;
    private Long chunkId;
    private Long sourceId;
    private String sourceTitle;
    private String sectionTitle;
    private Integer pageNumber;
    private String snippet;
    private boolean sourceAvailable;
}
