package com.eduhub.edu_hub.aiassistant.domain.repository;

import com.eduhub.edu_hub.aiassistant.domain.enums.KnowledgeSourceStatus;
import com.eduhub.edu_hub.aiassistant.domain.model.KnowledgeSource;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface KnowledgeSourceRepository
        extends JpaRepository<KnowledgeSource, Long>, JpaSpecificationExecutor<KnowledgeSource> {
    Optional<KnowledgeSource> findByIdAndDeletedFalse(Long id);

    Optional<KnowledgeSource> findByFileHashAndDeletedFalse(String fileHash);

    boolean existsByPrevSourceIdAndStatusInAndDeletedFalse(Long prevSourceId, List<KnowledgeSourceStatus> statuses);

    // Đọc/ghi tệp riêng để danh sách và thao tác trạng thái không tải hàng loạt BYTEA vào bộ nhớ.
    @Query(value = "select original_file from knowledge_sources where id = :id and not is_deleted", nativeQuery = true)
    byte[] findOriginalFile(@Param("id") Long id);

    @Modifying
    @Query(value = "update knowledge_sources set original_file = :originalFile where id = :id", nativeQuery = true)
    void storeOriginalFile(@Param("id") Long id, @Param("originalFile") byte[] originalFile);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select source from KnowledgeSource source where source.id = :id")
    Optional<KnowledgeSource> findForUpdate(@Param("id") Long id);

    @Query(
            "select source.id from KnowledgeSource source where source.status = :status and source.deleted = false and source.processingToken is null order by source.id")
    List<Long> findPendingIds(@Param("status") KnowledgeSourceStatus status, Pageable pageable);

    @Query(
            "select source.id from KnowledgeSource source where source.status = :status and source.deleted = false and source.processingStartedAt < :before order by source.id")
    List<Long> findInterruptedIds(
            @Param("status") KnowledgeSourceStatus status, @Param("before") LocalDateTime before, Pageable pageable);

    @Modifying
    @Query(
            value =
                    """
            update knowledge_sources previous set status = 'INACTIVE', updated_at = :auditTimestamp
            where previous.status = 'ACTIVE' and not previous.is_deleted
              and exists (select 1 from knowledge_sources replacement where replacement.prev_source_id = previous.id
                  and replacement.status = 'ACTIVE' and not replacement.is_deleted and replacement.effective_date <= :onDate)
            """,
            nativeQuery = true)
    int deactivateSupersededSources(
            @Param("onDate") LocalDate onDate, @Param("auditTimestamp") LocalDateTime auditTimestamp);
}
