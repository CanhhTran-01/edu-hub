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
@Table(name = "ai_chat_citations")
@Getter
@NoArgsConstructor
public class ChatCitation extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private ChatMessage message;

    @Column(name = "source_title", nullable = false)
    private String sourceTitle;

    @Column(name = "section_title")
    private String sectionTitle;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String snippet;

    public ChatCitation(
            ChatMessage message, String sourceTitle, String sectionTitle, Integer pageNumber, String snippet) {
        this.message = message;
        this.sourceTitle = sourceTitle;
        this.sectionTitle = sectionTitle;
        this.pageNumber = pageNumber;
        this.snippet = snippet;
    }
}
