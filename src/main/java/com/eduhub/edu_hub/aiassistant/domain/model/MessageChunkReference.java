package com.eduhub.edu_hub.aiassistant.domain.model;

import com.eduhub.edu_hub.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "message_chunk_references")
@Getter
@NoArgsConstructor
public class MessageChunkReference extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message;

    // NOTE: Snapshot cũ được giữ vì lịch sử trước migration chưa lưu ID chunk; không suy ghép theo tên/nội dung.
    private Long chunkId;
    private Double relevanceScore;

    @Column(name = "source_title")
    private String sourceTitle;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(columnDefinition = "TEXT")
    private String snippet;

    public MessageChunkReference(ChatMessage message, Long chunkId, Double relevanceScore) {
        this.message = message;
        this.chunkId = chunkId;
        this.relevanceScore = relevanceScore;
    }
}
