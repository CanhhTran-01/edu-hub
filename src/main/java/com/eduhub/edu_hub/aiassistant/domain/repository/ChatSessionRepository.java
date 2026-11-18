package com.eduhub.edu_hub.aiassistant.domain.repository;

import com.eduhub.edu_hub.aiassistant.domain.model.ChatSession;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long>, JpaSpecificationExecutor<ChatSession> {
    Optional<ChatSession> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select session from ChatSession session where session.id = :sessionId and session.userId = :userId and session.deleted = false")
    Optional<ChatSession> findOwnedForUpdate(@Param("sessionId") Long sessionId, @Param("userId") Long userId);
}
