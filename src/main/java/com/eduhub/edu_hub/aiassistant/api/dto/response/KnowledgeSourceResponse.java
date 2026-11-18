package com.eduhub.edu_hub.aiassistant.api.dto.response;

import com.eduhub.edu_hub.aiassistant.domain.enums.KnowledgeSourceStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record KnowledgeSourceResponse(
        Long id,
        Long uploadedBy,
        Long prevSourceId,
        String title,
        String documentType,
        String originalFilename,
        LocalDateTime uploadedAt,
        LocalDate effectiveDate,
        LocalDate expiryDate,
        KnowledgeSourceStatus status,
        String errorMessage,
        String embeddingModel,
        String embeddingModelVersion,
        long chunkCount) {}
