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
            """
            select message from ChatMessage message join message.session session
            where session.id = :sessionId and session.userId = :userId
              and session.deleted = false and message.deleted = false
              and message.role = com.eduhub.edu_hub.aiassistant.domain.enums.ChatMessageRole.ASSISTANT
              and message.content like :payloadPattern
            order by message.id desc
            """)
    List<ChatMessage> findAcademicContextMessages(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId,
            @Param("payloadPattern") String payloadPattern,
            Pageable pageable);

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
