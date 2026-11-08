package com.eduhub.edu_hub.identity.application.facade.impl;

import com.eduhub.edu_hub.identity.api.dto.request.CreateLecturerAccountRequest;
import com.eduhub.edu_hub.identity.api.dto.request.CreateStudentAccountRequest;
import com.eduhub.edu_hub.identity.api.dto.response.AccountNotificationResponse;
import com.eduhub.edu_hub.identity.api.dto.response.StudentAccountResponse;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.exception.IdentityAccountException;
import com.eduhub.edu_hub.identity.application.exception.IdentityAuthenticationException;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.identity.application.service.TokenService;
import com.eduhub.edu_hub.identity.domain.enums.RoleName;
import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.Role;
import com.eduhub.edu_hub.identity.domain.model.User;
import com.eduhub.edu_hub.identity.domain.repository.RefreshTokenRepository;
import com.eduhub.edu_hub.identity.domain.repository.RoleRepository;
import com.eduhub.edu_hub.identity.domain.repository.UserRepository;
import com.eduhub.edu_hub.notification.application.command.SendAccountEmailCommand;
import com.eduhub.edu_hub.notification.application.command.SendAdmissionConfirmationEmailCommand;
import com.eduhub.edu_hub.notification.application.facade.NotificationFacade;
import com.eduhub.edu_hub.notification.domain.enums.EmailTemplateType;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdentityFacadeImpl implements IdentityFacade {
    private final UserRepository users;
    private final RoleRepository roles;
    private final RefreshTokenRepository sessions;
    private final TokenService tokens;
    private final NotificationFacade.ImmediateEmail immediateEmailNotifications;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    @Transactional
    public Long createLecturerAccountWithoutNotification(CreateLecturerAccountRequest request) {
        if (users.existsByUsername(request.username())) {
            throw new IdentityAccountException(ErrorCode.DUPLICATE_LECTURER_CODE);
        }
        Role role = roles.findByRoleNameAndDeletedFalse(RoleName.LECTURER.name())
                .orElseThrow(() -> new IdentityAccountException(ErrorCode.VALIDATION_ERROR));
        return save(request.username(), request.email(), request.initialPassword(), role, true)
                .getId();
    }

    @Override
    @Transactional(readOnly = true)
    public AccountNotificationResponse sendLecturerAccountNotification(Long userId, String initialPassword) {

        User account = user(userId);

        // Kiểm tra loại tài khoản nhận thông báo, không phải quyền của người gọi.
        if (account.isDeleted()
                || !RoleName.LECTURER.name().equals(account.getRole().getRoleName())
                || account.getStatus() != UserStatus.ACTIVE) {
            return new AccountNotificationResponse(userId, false, "ACCOUNT_NOT_AVAILABLE");
        }

        if (!encoder.matches(initialPassword, account.getPasswordHash())) {
            return new AccountNotificationResponse(userId, false, "INITIAL_PASSWORD_NO_LONGER_VALID");
        }

        return sendStudentAccountNotification(userId, initialPassword);
    }

    @Override
    @Transactional
    public Long createStudentAccountWithoutNotification(CreateStudentAccountRequest request) {
        return createStudentAccountRecord(request);
    }

    @Override
    public AccountNotificationResponse sendStudentAccountNotification(Long userId, String initialPassword) {

        User user = user(userId);

        if (!user.isFirstLogin()) {
            return new AccountNotificationResponse(userId, false, "ACCOUNT_ALREADY_ACTIVATED");
        }

        if (user.isDeleted()
                || user.getStatus() != UserStatus.ACTIVE
                || (user.getLockedUntil() != null
                        && user.getLockedUntil().isAfter(LocalDateTime.now(java.time.ZoneOffset.UTC)))) {
            return new AccountNotificationResponse(userId, false, "ACCOUNT_NOT_AVAILABLE");
        }

        if (!encoder.matches(initialPassword, user.getPasswordHash())) {
            return new AccountNotificationResponse(userId, false, "INITIAL_PASSWORD_NO_LONGER_VALID");
        }

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return new AccountNotificationResponse(userId, false, "ACCOUNT_EMAIL_MISSING");
        }

        var delivery = immediateEmailNotifications.sendAccountEmail(SendAccountEmailCommand.builder()
                .recipient(user.getEmail())
                .username(user.getUsername())
                .temporaryPassword(initialPassword)
                .templateType(EmailTemplateType.ACCOUNT_CREATED)
                .build());

        if (!delivery.sent()) log.error("Initial account email failed for user {}", userId);
        return new AccountNotificationResponse(userId, delivery.sent(), delivery.error());
    }

    @Override
    @Transactional(readOnly = true)
    public UserInfoResponse requireCurrentUser(String accessToken) {

        // TODO(AUTHENTICATED_PRINCIPAL): Khi refactor hồ sơ cá nhân, chuyển contract People–Identity sang danh tính
        // đã xác thực từ SecurityContext để bỏ giải mã lại token; hiện giữ tương thích vì ngoài phạm vi login/logout.

        TokenService.TokenClaims claims = tokens.validateAccessToken(accessToken);

        if (claims == null) throw new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED);
        User current = users.findByUsernameAndDeletedFalse(claims.username())
                .orElseThrow(() -> new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED));

        LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);

        boolean validSession = sessions.findByIdAndRevokedFalse(claims.sessionId())
                .filter(session -> session.getUser().getId().equals(current.getId()))
                .filter(session -> now.isBefore(session.getExpiresAt()))
                .isPresent();

        if (!current.getId().equals(claims.userId())
                || current.getStatus() != UserStatus.ACTIVE
                || (current.getLockedUntil() != null && now.isBefore(current.getLockedUntil()))
                || !validSession) {
            throw new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED);
        }

        return response(current);
    }

    @Override
    public StudentAccountResponse getStudentAccount(Long userId) {
        User account = user(userId);
        if (account.isDeleted()
                || !RoleName.STUDENT.name().equals(account.getRole().getRoleName())) {
            throw new IdentityAccountException(ErrorCode.USER_NOT_FOUND);
        }
        boolean locked = account.getStatus() != UserStatus.ACTIVE
                || (account.getLockedUntil() != null
                        && account.getLockedUntil().isAfter(LocalDateTime.now(java.time.ZoneOffset.UTC)));
        return new StudentAccountResponse(
                account.getId(),
                account.getUsername(),
                account.getEmail(),
                account.getStatus().name(),
                account.isFirstLogin(),
                locked);
    }

    @Override
    public AccountNotificationResponse sendStudentAdmissionNotification(Long userId, String recipient) {
        var account = getStudentAccount(userId);
        if (recipient == null || recipient.isBlank()) {
            return new AccountNotificationResponse(userId, false, "ACCOUNT_EMAIL_MISSING");
        }
        var delivery = immediateEmailNotifications.sendAdmissionConfirmationEmail(
                SendAdmissionConfirmationEmailCommand.builder()
                        .recipient(recipient)
                        .username(account.username())
                        .build());
        if (!delivery.sent()) {
            log.error("Admission confirmation email failed for user {}", userId);
        }
        return new AccountNotificationResponse(userId, delivery.sent(), delivery.error());
    }

    private Long createStudentAccountRecord(CreateStudentAccountRequest request) {

        if (users.existsByUsername(request.username())) {
            throw new IdentityAccountException(ErrorCode.DUPLICATE_STUDENT_CODE);
        }

        Role role = roles.findByRoleNameAndDeletedFalse(RoleName.STUDENT.name())
                .orElseThrow(() -> new IdentityAccountException(ErrorCode.VALIDATION_ERROR));

        return save(
                        request.username(),
                        request.email(),
                        request.initialPassword(),
                        role,
                        request.requirePasswordChange())
                .getId();
    }

    private User save(String username, String email, String rawPassword, Role role, boolean firstLogin) {
        User user = new User();

        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(rawPassword));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setFirstLogin(firstLogin);

        return users.save(user);
    }

    @Override
    public boolean existsByUsername(String username) {
        return users.existsByUsername(username);
    }

    private User user(Long id) {
        return users.findById(id).orElseThrow(() -> new IdentityAccountException(ErrorCode.USER_NOT_FOUND));
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
