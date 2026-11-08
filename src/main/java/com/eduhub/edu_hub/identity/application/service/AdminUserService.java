package com.eduhub.edu_hub.identity.application.service;

import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.exception.IdentityAccountException;
import com.eduhub.edu_hub.identity.application.exception.IdentityAuthenticationException;
import com.eduhub.edu_hub.identity.domain.enums.RoleName;
import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.User;
import com.eduhub.edu_hub.identity.domain.repository.RefreshTokenRepository;
import com.eduhub.edu_hub.identity.domain.repository.UserRepository;
import com.eduhub.edu_hub.notification.application.command.SendAccountEmailCommand;
import com.eduhub.edu_hub.notification.application.facade.NotificationFacade;
import com.eduhub.edu_hub.notification.domain.enums.EmailTemplateType;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserService {
    private final UserRepository users;
    private final RefreshTokenRepository sessions;
    private final TokenService tokens;
    private final NotificationFacade.ImmediateEmail immediateEmailNotifications;
    private final PasswordEncoder encoder;

    @Transactional(readOnly = true)
    public PageResponse<UserInfoResponse> search(Jwt authenticatedJwt, String keyword, int page, int size) {
        actor(authenticatedJwt);
        Page<UserInfoResponse> result = users.findByDeletedFalseAndUsernameContainingIgnoreCase(
                        keyword == null ? "" : keyword, PageRequest.of(page, size))
                .map(this::response);
        return PageResponse.<UserInfoResponse>builder()
                .content(result.getContent())
                .pageNumber(result.getNumber())
                .pageSize(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .isLast(result.isLast())
                .build();
    }

    @Transactional
    public void lock(Jwt authenticatedJwt, Long id, String reason) {
        User admin = actor(authenticatedJwt);
        User target = target(id);
        if (admin.getId().equals(id)
                && users.countByDeletedFalseAndStatusAndRole_RoleName(UserStatus.ACTIVE, RoleName.SYSTEM_ADMIN.name())
                        <= 1) {
            throw new IdentityAccountException(ErrorCode.UNAUTHORIZED);
        }
        target.setStatus(UserStatus.LOCKED);
        revoke(target);
        log.info("Admin {} locked user {}. Reason: {}", admin.getId(), id, reason);
    }

    @Transactional
    public void unlock(Jwt authenticatedJwt, Long id) {
        User admin = actor(authenticatedJwt);
        User target = target(id);
        target.setStatus(UserStatus.ACTIVE);
        target.setLockedUntil(null);
        target.setFailedLoginCount(0);
        log.info("Admin {} unlocked user {}", admin.getId(), id);
    }

    @Transactional
    public void resetPassword(Jwt authenticatedJwt, Long id) {
        User admin = actor(authenticatedJwt);
        User target =
                users.findByIdForUpdate(id).orElseThrow(() -> new IdentityAccountException(ErrorCode.USER_NOT_FOUND));
        if (target.getEmail() == null || target.getEmail().isBlank()) {
            throw new IdentityAccountException(ErrorCode.ACCOUNT_EMAIL_MISSING);
        }
        String temporaryPassword = tokens.newRefreshToken();
        // Khóa cùng User như đổi mật khẩu để không ghi đè lịch sử khi hai thao tác chạy đồng thời.
        // Reset liên tiếp chỉ thay mật khẩu tạm, vẫn giữ mật khẩu chính thức gần nhất.
        if (!target.isFirstLogin()) target.setPreviousPasswordHash(target.getPasswordHash());
        target.setPasswordHash(encoder.encode(temporaryPassword));
        target.setFirstLogin(true);
        target.setPasswordChangedAt(LocalDateTime.now(ZoneOffset.UTC));
        revoke(target);
        // NOTE: Chỉ revoke refresh token; access token đã phát vẫn được Resource Server chấp nhận đến exp.
        // TODO(ACCESS_TOKEN_REVOCATION): Đồng bộ với đổi mật khẩu/logout khi triển khai thu hồi jti bằng Redis;
        // hiện không kiểm tra phiên DB trên mọi API nên chưa bảo đảm kết thúc ngay toàn bộ truy cập cũ.
        var delivery = immediateEmailNotifications.sendAccountEmail(SendAccountEmailCommand.builder()
                .recipient(target.getEmail())
                .username(target.getUsername())
                .temporaryPassword(temporaryPassword)
                .templateType(EmailTemplateType.PASSWORD_RESET)
                .build());
        if (!delivery.sent()) {
            log.error("Password reset email failed for user {}", id);
            throw new IdentityAccountException(ErrorCode.PASSWORD_EMAIL_FAILED);
        }
        log.info("Admin {} reset password for user {}", admin.getId(), id);
    }

    private User actor(Jwt authenticatedJwt) {
        // NOTE: Spring đã xác thực JWT; các truy vấn dưới đây là kiểm tra nghiệp vụ User/phiên, không giải mã lại
        // token.
        // TODO(ADMIN_USER_AUTHENTICATION): Khi refactor quản trị tài khoản, thống nhất kiểm tra hạn phiên/khóa tạm
        // với luồng đổi mật khẩu và hồ sơ cá nhân. Đợt login/logout giữ nguyên điều kiện DB và TODO phân quyền hiện
        // tại.
        TokenService.TokenClaims claims = tokens.requireAuthenticatedClaims(authenticatedJwt);
        User actor = users.findByUsernameAndDeletedFalse(claims.username())
                .orElseThrow(() -> new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED));
        if (!actor.getId().equals(claims.userId())
                || actor.getStatus() != UserStatus.ACTIVE
                || sessions.findByIdAndRevokedFalse(claims.sessionId()).stream()
                        .noneMatch(session -> session.getUser().getId().equals(actor.getId()))) {
            throw new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED);
        }
        // TODO(AUTHORIZATION): Bổ sung phân quyền quản trị tài khoản bằng Spring Security sau.
        return actor;
    }

    private User target(Long id) {
        User user = users.findById(id).orElseThrow(() -> new IdentityAccountException(ErrorCode.USER_NOT_FOUND));
        if (user.isDeleted()) throw new IdentityAccountException(ErrorCode.USER_NOT_FOUND);
        return user;
    }

    private void revoke(User user) {
        sessions.findByUser_IdAndRevokedFalse(user.getId()).forEach(session -> session.setRevoked(true));
    }

    private UserInfoResponse response(User user) {
        return UserInfoResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().getRoleName())
                .status(user.getStatus().name())
                .isFirstLogin(user.isFirstLogin())
                .build();
    }
}
