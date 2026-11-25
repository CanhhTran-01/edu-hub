package com.eduhub.edu_hub.aiassistant.domain.repository;

import com.eduhub.edu_hub.aiassistant.domain.model.MessageChunkReference;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageChunkReferenceRepository extends JpaRepository<MessageChunkReference, Long> {
    @Query(
            value =
                    """
            select reference.message_id as "messageId", reference.chunk_id as "chunkId",
                   source.id as "sourceId", coalesce(source.title, reference.source_title) as "sourceTitle",
                   coalesce(chunk.page_number, reference.page_number) as "pageNumber",
                   coalesce(chunk.content, reference.snippet) as snippet,
                   (source.status = 'ACTIVE' and not source.is_deleted and not chunk.is_deleted
                    and source.effective_date <= :onDate and (source.expiry_date is null or source.expiry_date >= :onDate)
                    and not exists (select 1 from knowledge_sources replacement
                        where replacement.prev_source_id = source.id and replacement.status = 'ACTIVE'
                        and not replacement.is_deleted and replacement.effective_date <= :onDate)) as "sourceAvailable"
            from message_chunk_references reference
            join chat_messages message on message.id = reference.message_id
            join chat_sessions session on session.id = message.session_id
            left join knowledge_chunks chunk on chunk.id = reference.chunk_id
            left join knowledge_sources source on source.id = chunk.source_id
            where reference.message_id in (:messageIds) and session.user_id = :userId
              and not session.is_deleted and not message.is_deleted and not reference.is_deleted
            order by reference.id
            """,
            nativeQuery = true)
    List<ReferenceProjection> findOwnedReferences(
            @Param("userId") Long userId,
            @Param("messageIds") List<Long> messageIds,
            @Param("onDate") LocalDate onDate);

    interface ReferenceProjection {
        Long getMessageId();

        Long getChunkId();

        Long getSourceId();

        String getSourceTitle();

        Integer getPageNumber();

        String getSnippet();

        Boolean getSourceAvailable();
    }
}
