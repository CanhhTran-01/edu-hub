package com.eduhub.edu_hub.aiassistant.domain.model;

import com.eduhub.edu_hub.shared.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Metadata và nội dung chunk; vector cùng hàng được adapter JDBC đọc/ghi bằng toán tử pgvector. */
@Entity
@Table(name = "knowledge_chunks")
@Getter
@NoArgsConstructor
public class KnowledgeChunk extends BaseEntity {
    @Column(nullable = false)
    private Long sourceId;

    private Integer chunkIndex;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String embeddingModel;
    private String embeddingModelVersion;
    private Integer tokenCount;
    private Integer pageNumber;
}
