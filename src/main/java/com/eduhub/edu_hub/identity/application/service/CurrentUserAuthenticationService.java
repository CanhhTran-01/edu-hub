package com.eduhub.edu_hub.identity.application.service;

import com.eduhub.edu_hub.identity.application.exception.IdentityAuthenticationException;
import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.User;
import com.eduhub.edu_hub.identity.domain.repository.RefreshTokenRepository;
import com.eduhub.edu_hub.identity.domain.repository.UserRepository;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Kiểm tra tài khoản/phiên trong DB từ principal đã xác thực; User chỉ được dùng nội bộ Identity. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrentUserAuthenticationService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenService tokenService;

    public User requireCurrentUser(Jwt authenticatedJwt) {
        // NOTE: Spring đã xác thực JWT; các truy vấn dưới đây là kiểm User/phiên, không giải mã lại token.
        TokenService.TokenClaims claims = tokenService.requireAuthenticatedClaims(authenticatedJwt);

        User currentUser = userRepository
                .findByUsernameAndDeletedFalse(claims.username())
                .orElseThrow(() -> new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED));

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        boolean hasValidSession = refreshTokenRepository
                .findByIdAndRevokedFalse(claims.sessionId())
                .filter(session -> session.getUser().getId().equals(currentUser.getId()))
                .filter(session -> now.isBefore(session.getExpiresAt()))
                .isPresent();

        if (!currentUser.getId().equals(claims.userId())
                || currentUser.isDeleted()
                || currentUser.getStatus() != UserStatus.ACTIVE
                || (currentUser.getLockedUntil() != null && now.isBefore(currentUser.getLockedUntil()))
                || !hasValidSession) {
            throw new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED);
        }

        return currentUser;
    }
}
