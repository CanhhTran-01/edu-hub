package com.eduhub.edu_hub.identity.application;

import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.repository.RefreshTokenRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccessSessionService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenService tokenService;

    @Transactional(readOnly = true)
    public boolean isActive(String accessToken) {
        TokenService.TokenClaims claims = tokenService.validateAccessToken(accessToken);
        if (claims == null) return false;
        return refreshTokenRepository.findByIdAndRevokedFalse(claims.sessionId())
                .filter(session -> session.getUser().getId().equals(claims.userId()))
                .filter(session -> session.getUser().getUsername().equals(claims.username()))
                .filter(session -> session.getUser().getStatus() == UserStatus.ACTIVE)
                .filter(session -> !session.getUser().isDeleted())
                .filter(session -> LocalDateTime.now(ZoneOffset.UTC).isBefore(session.getExpiresAt()))
                .isPresent();
    }
}
