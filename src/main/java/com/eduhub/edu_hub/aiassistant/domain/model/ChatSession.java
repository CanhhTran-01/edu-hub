package com.eduhub.edu_hub.aiassistant.domain.model;

import com.eduhub.edu_hub.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_sessions")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession extends BaseEntity {
    @Column(name = "user_id", nullable = false)
    private Long userId;

    private String title;
    private LocalDateTime startedAt;
    private LocalDateTime lastMessageAt;

    @Column(nullable = false, length = 32)
    private String topicType = "KNOWLEDGE";

    public ChatSession(Long userId, String title, LocalDateTime startedAt) {
        this.userId = userId;
        this.title = title;
        this.startedAt = startedAt;
        this.lastMessageAt = startedAt;
    }

    public void recordMessageAt(LocalDateTime sentAt) {
        lastMessageAt = sentAt;
    }
}
