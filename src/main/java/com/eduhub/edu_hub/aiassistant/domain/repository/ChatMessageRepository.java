package com.eduhub.edu_hub.aiassistant.domain.repository;

import com.eduhub.edu_hub.aiassistant.domain.model.ChatMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySessionIdAndDeletedFalseOrderByIdDesc(Long sessionId, Pageable pageable);

    List<ChatMessage> findBySessionIdAndDeletedFalseOrderByIdAsc(Long sessionId);
}
