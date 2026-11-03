package com.eduhub.edu_hub.identity.application;

import com.eduhub.edu_hub.identity.api.dto.UserInfoResponse;
import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.User;
import com.eduhub.edu_hub.identity.domain.repository.RefreshTokenRepository;
import com.eduhub.edu_hub.identity.domain.repository.UserRepository;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.enums.RoleName;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.mail.MailException;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserService {
    private final UserRepository users;
    private final RefreshTokenRepository sessions;
    private final TokenService tokens;
    private final TemporaryPasswordMailService mailService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Transactional(readOnly = true)
    public PageResponse<UserInfoResponse> search(String bearer, String keyword, int page, int size) {
        actor(bearer);
        Page<UserInfoResponse> result = users.findByDeletedFalseAndUsernameContainingIgnoreCase(
                keyword == null ? "" : keyword, PageRequest.of(page, size)).map(this::response);
        return PageResponse.<UserInfoResponse>builder().content(result.getContent()).pageNumber(result.getNumber())
                .pageSize(result.getSize()).totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages()).isLast(result.isLast()).build();
    }

    @Transactional
    public void lock(String bearer, Long id, String reason) {
        User admin = actor(bearer);
        User target = target(id);
        if (admin.getId().equals(id) && users.countByDeletedFalseAndStatusAndRole_RoleName(
                UserStatus.ACTIVE, RoleName.SYSTEM_ADMIN.name()) <= 1) {
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        }
        target.setStatus(UserStatus.LOCKED);
        revoke(target);
        log.info("Admin {} locked user {}. Reason: {}", admin.getId(), id, reason);
    }

    @Transactional
    public void unlock(String bearer, Long id) {
        User admin = actor(bearer);
        User target = target(id);
        target.setStatus(UserStatus.ACTIVE);
        target.setLockedUntil(null);
        target.setFailedLoginCount(0);
        log.info("Admin {} unlocked user {}", admin.getId(), id);
    }

    @Transactional
    public void resetPassword(String bearer, Long id) {
        User admin = actor(bearer);
        User target = target(id);
        if (target.getEmail() == null || target.getEmail().isBlank()) {
            throw new BaseException(ErrorCode.ACCOUNT_EMAIL_MISSING);
        }
        String temporaryPassword = tokens.newRefreshToken();
        target.setPasswordHash(encoder.encode(temporaryPassword));
        target.setFirstLogin(true);
        target.setPasswordChangedAt(LocalDateTime.now(ZoneOffset.UTC));
        revoke(target);
        try {
            mailService.send(target.getEmail(), temporaryPassword);
        } catch (MailException exception) {
            log.error("Password reset email failed for user {}", id);
            throw new BaseException(ErrorCode.PASSWORD_EMAIL_FAILED);
        }
        log.info("Admin {} reset password for user {}", admin.getId(), id);
    }

    private User actor(String bearer) {
        TokenService.TokenClaims claims = tokens.validateAccessToken(bearer);
        if (claims == null) throw new BaseException(ErrorCode.UNAUTHENTICATED);
        User actor = users.findByUsernameAndDeletedFalse(claims.username())
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHENTICATED));
        if (!actor.getId().equals(claims.userId()) || actor.getStatus() != UserStatus.ACTIVE ||
                sessions.findByIdAndRevokedFalse(claims.sessionId()).stream()
                        .noneMatch(session -> session.getUser().getId().equals(actor.getId()))) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED);
        }
        if (!RoleName.SYSTEM_ADMIN.name().equals(actor.getRole().getRoleName())) {
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        }
        return actor;
    }

    private User target(Long id) {
        User user = users.findById(id).orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
        if (user.isDeleted()) throw new BaseException(ErrorCode.USER_NOT_FOUND);
        return user;
    }

    private void revoke(User user) {
        sessions.findByUser_IdAndRevokedFalse(user.getId()).forEach(session -> session.setRevoked(true));
    }

    private UserInfoResponse response(User user) {
        return UserInfoResponse.builder().id(user.getId()).username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().getRoleName()).status(user.getStatus().name())
                .isFirstLogin(user.isFirstLogin()).build();
    }
}
