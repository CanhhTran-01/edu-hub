package com.eduhub.edu_hub.identity.domain.repository;

import com.eduhub.edu_hub.identity.domain.model.RefreshToken;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    // Chỉ đọc ID trước khi khóa User, tránh giữ entity có trạng thái cũ trong persistence context.
    @Query("select token.user.id from RefreshToken token where token.tokenHash = :tokenHash and token.deleted = false")
    Optional<Long> findUserIdByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token where token.tokenHash = :tokenHash and token.deleted = false")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<RefreshToken> findByTokenFamilyIdAndRevokedFalse(UUID tokenFamilyId);

    // Bỏ qua hàng đang bị khóa để các lượt dọn không chờ nhau; mỗi lô có transaction riêng ở service.
    @Modifying
    @Query(
            value =
                    """
            delete from refresh_tokens where id in (
                select id from refresh_tokens where expires_at <= :expiredAt
                order by expires_at, id limit :batchSize for update skip locked
            )
            """,
            nativeQuery = true)
    int deleteExpiredBatch(@Param("expiredAt") LocalDateTime expiredAt, @Param("batchSize") int batchSize);

    @EntityGraph(attributePaths = "user")
    Optional<RefreshToken> findByIdAndRevokedFalse(Long id);

    List<RefreshToken> findByUser_IdAndRevokedFalse(Long userId);

    void deleteAllByUser_Id(Long userId);
}
