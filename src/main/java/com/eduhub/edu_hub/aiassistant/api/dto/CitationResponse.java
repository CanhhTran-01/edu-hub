package com.eduhub.edu_hub.aiassistant.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CitationResponse {
    private String sourceTitle;
    private String sectionTitle;
    private Integer pageNumber;
    private String snippet;
}
