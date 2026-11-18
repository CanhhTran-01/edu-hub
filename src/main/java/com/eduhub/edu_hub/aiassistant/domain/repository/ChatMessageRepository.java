package com.eduhub.edu_hub.aiassistant.domain.repository;

import com.eduhub.edu_hub.aiassistant.domain.model.ChatMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySessionIdAndSessionUserIdAndSessionDeletedFalseAndDeletedFalseOrderByIdDesc(
            Long sessionId, Long userId, Pageable pageable);

    List<ChatMessage> findBySessionIdAndSessionUserIdAndSessionDeletedFalseAndDeletedFalseOrderByIdAsc(
            Long sessionId, Long userId);

    @Query(
            value =
                    """
            select distinct on (message.session_id) message.session_id as "sessionId", message.content as content
            from chat_messages message join chat_sessions session on session.id = message.session_id
            where session.user_id = :userId and not session.is_deleted and not message.is_deleted
            and session.id in (:sessionIds) order by message.session_id, message.id desc
            """,
            nativeQuery = true)
    List<LastMessageProjection> findLastMessages(
            @Param("userId") Long userId, @Param("sessionIds") List<Long> sessionIds);

    interface LastMessageProjection {
        Long getSessionId();

        String getContent();
    }
}
