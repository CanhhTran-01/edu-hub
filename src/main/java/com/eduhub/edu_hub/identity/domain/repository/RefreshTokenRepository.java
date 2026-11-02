package com.eduhub.edu_hub.identity.domain.repository;

import com.eduhub.edu_hub.identity.domain.model.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHashAndRevokedFalse(String tokenHash);

    @EntityGraph(attributePaths = "user")
    Optional<RefreshToken> findByIdAndRevokedFalse(Long id);
}
