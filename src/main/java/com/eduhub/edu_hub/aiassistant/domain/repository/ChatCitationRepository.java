package com.eduhub.edu_hub.aiassistant.domain.repository;

import com.eduhub.edu_hub.aiassistant.domain.model.ChatCitation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatCitationRepository extends JpaRepository<ChatCitation, Long> {
    List<ChatCitation> findByMessageIdInAndDeletedFalseOrderByIdAsc(List<Long> messageIds);
}
