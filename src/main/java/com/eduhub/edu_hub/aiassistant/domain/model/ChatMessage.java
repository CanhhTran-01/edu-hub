package com.eduhub.edu_hub.aiassistant.domain.model;

import com.eduhub.edu_hub.aiassistant.domain.enums.ChatMessageRole;
import com.eduhub.edu_hub.shared.common.BaseEntity;
import com.eduhub.edu_hub.shared.enums.AiIntent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ai_chat_messages")
@Getter
@NoArgsConstructor
public class ChatMessage extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ChatMessageRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private AiIntent intent;

    public ChatMessage(ChatSession session, ChatMessageRole role, String content, AiIntent intent) {
        this.session = session;
        this.role = role;
        this.content = content;
        this.intent = intent;
    }
}
