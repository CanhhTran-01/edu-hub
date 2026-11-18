package com.eduhub.edu_hub.aiassistant.domain.model;

import com.eduhub.edu_hub.aiassistant.domain.enums.KnowledgeSourceStatus;
import com.eduhub.edu_hub.shared.common.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

/** Nguồn và tệp gốc thuộc AI; chỉ nguồn xử lý xong, được bật và còn hiệu lực mới được truy xuất. */
@Entity
@Table(name = "knowledge_sources")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSource extends BaseEntity {
    private Long uploadedBy;
    private Long prevSourceId;

    @Column(nullable = false)
    private String title;

    private String documentType;
    private String originalFilename;

    private String fileHash;
    private LocalDateTime uploadedAt;
    private LocalDate effectiveDate;
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private KnowledgeSourceStatus status;

    @Column(length = 500)
    private String errorMessage;

    private String embeddingModel;
    private String embeddingModelVersion;
    private String processingToken;
    private LocalDateTime processingStartedAt;
}
