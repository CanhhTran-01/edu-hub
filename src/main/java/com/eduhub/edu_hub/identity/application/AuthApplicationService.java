package com.eduhub.edu_hub.identity.application;

import com.eduhub.edu_hub.identity.api.dto.LoginRequest;
import com.eduhub.edu_hub.identity.api.dto.ChangePasswordRequest;
import com.eduhub.edu_hub.identity.api.dto.LoginResponse;
import com.eduhub.edu_hub.identity.api.dto.UserInfoResponse;
import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.RefreshToken;
import com.eduhub.edu_hub.identity.domain.model.User;
import com.eduhub.edu_hub.identity.domain.repository.RefreshTokenRepository;
import com.eduhub.edu_hub.identity.domain.repository.UserRepository;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class AuthApplicationService {
    private static final int MAX_FAILED_LOGINS = 5;
    private static final Duration TEMPORARY_LOCK = Duration.ofMinutes(15);
    private static final Duration ACCESS_LIFETIME = Duration.ofMinutes(15);
    private static final Duration REFRESH_LIFETIME = Duration.ofDays(7);
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenService tokenService;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final PlatformTransactionManager transactionManager;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public LoginResponse login(LoginRequest request) {
        LoginAttempt attempt = new TransactionTemplate(transactionManager).execute(status -> authenticate(request));
        if (attempt == null) throw new IllegalStateException("Login transaction returned no result");
        if (attempt.error() != null) throw new BaseException(attempt.error());
        return attempt.response();
    }

    private LoginAttempt authenticate(LoginRequest request) {
        User user = userRepository
                .findByUsernameForUpdate(request.getUsername())
                .orElse(null);
        if (user == null) {
            return new LoginAttempt(null, ErrorCode.INVALID_CREDENTIALS);
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (user.getStatus() == UserStatus.DISABLED || user.getStatus() == UserStatus.LOCKED) {
            return new LoginAttempt(null, ErrorCode.ACCOUNT_LOCKED);
        }
        if (user.getLockedUntil() != null && now.isBefore(user.getLockedUntil())) {
            return new LoginAttempt(null, ErrorCode.ACCOUNT_LOCKED);
        }
        if (user.getLockedUntil() != null) {
            user.setLockedUntil(null);
            user.setFailedLoginCount(0);
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            int failures = user.getFailedLoginCount() + 1;
            user.setFailedLoginCount(failures);
            if (failures >= MAX_FAILED_LOGINS) {
                user.setLockedUntil(now.plus(TEMPORARY_LOCK));
            }
            userRepository.save(user);
            return new LoginAttempt(null, ErrorCode.INVALID_CREDENTIALS);
        }
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        Instant issuedAt = now.toInstant(ZoneOffset.UTC);
        String refreshToken = tokenService.newRefreshToken();
        RefreshToken session = new RefreshToken();
        session.setUser(user);
        session.setTokenHash(tokenService.hash(refreshToken));
        session.setExpiresAt(now.plus(REFRESH_LIFETIME));
        session = refreshTokenRepository.saveAndFlush(session);
        UserInfoResponse info = UserInfoResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole().getRoleName())
                .isFirstLogin(user.isFirstLogin())
                .status(user.getStatus().name())
                .build();
        LoginResponse response = LoginResponse.builder()
                .accessToken(tokenService.accessToken(user, session.getId(), issuedAt, issuedAt.plus(ACCESS_LIFETIME)))
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(ACCESS_LIFETIME.toSeconds())
                .user(info)
                .requirePasswordChange(user.isFirstLogin())
                .build();
        return new LoginAttempt(response, null);
    }

    public void logout(String accessToken, String refreshToken) {
        TokenService.TokenClaims claims = tokenService.validateAccessToken(accessToken);
        if (claims == null) throw new BaseException(ErrorCode.UNAUTHENTICATED);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            RefreshToken token = refreshTokenRepository
                    .findByTokenHashAndRevokedFalse(tokenService.hash(refreshToken))
                    .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHENTICATED));
            if (!LocalDateTime.now(ZoneOffset.UTC).isBefore(token.getExpiresAt())) {
                throw new BaseException(ErrorCode.UNAUTHENTICATED);
            }
            if (!token.getId().equals(claims.sessionId()) || !token.getUser().getId().equals(claims.userId())) {
                throw new BaseException(ErrorCode.UNAUTHENTICATED);
            }
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    public void changePassword(String accessToken, ChangePasswordRequest request) {
        TokenService.TokenClaims claims = tokenService.validateAccessToken(accessToken);
        if (claims == null) throw new BaseException(ErrorCode.UNAUTHENTICATED);
        ErrorCode error = new TransactionTemplate(transactionManager).execute(status -> {
            User user = userRepository.findByUsernameForUpdate(claims.username())
                    .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHENTICATED));
            if (!user.getId().equals(claims.userId()) || user.getStatus() != UserStatus.ACTIVE
                    || refreshTokenRepository.findByIdAndRevokedFalse(claims.sessionId()).stream()
                            .noneMatch(session -> session.getUser().getId().equals(user.getId()))) {
                return ErrorCode.UNAUTHENTICATED;
            }
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            if (user.getLockedUntil() != null && now.isBefore(user.getLockedUntil())) {
                return ErrorCode.ACCOUNT_LOCKED;
            }
            if (user.getLockedUntil() != null) {
                user.setLockedUntil(null);
                user.setFailedLoginCount(0);
            }
            if (!user.isFirstLogin() && (request.getCurrentPassword() == null
                    || !passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash()))) {
                int failures = user.getFailedLoginCount() + 1;
                user.setFailedLoginCount(failures);
                if (failures >= MAX_FAILED_LOGINS) user.setLockedUntil(now.plus(TEMPORARY_LOCK));
                userRepository.save(user);
                return ErrorCode.INVALID_CREDENTIALS;
            }
            passwordPolicyValidator.validate(request.getNewPassword(), request.getConfirmPassword(),
                    passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash()));
            user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
            user.setPasswordChangedAt(now);
            user.setFirstLogin(false);
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
            userRepository.save(user);
            refreshTokenRepository.findByUser_IdAndRevokedFalse(user.getId()).forEach(session -> session.setRevoked(true));
            return null;
        });
        if (error != null) throw new BaseException(error);
    }

    private record LoginAttempt(LoginResponse response, ErrorCode error) {}
}
