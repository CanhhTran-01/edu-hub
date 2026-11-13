package com.eduhub.edu_hub.aiassistant.domain.model;

import com.eduhub.edu_hub.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ai_chat_sessions")
@Getter
@NoArgsConstructor
public class ChatSession extends BaseEntity {
    @Column(name = "user_id", nullable = false)
    private Long userId;

    public ChatSession(Long userId) {
        this.userId = userId;
    }
}
