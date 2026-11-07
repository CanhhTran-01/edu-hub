package com.eduhub.edu_hub.identity.application.facade.impl;

import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.*;
import com.eduhub.edu_hub.identity.application.command.CreateLecturerAccountCommand;
import com.eduhub.edu_hub.identity.application.command.CreateStudentAccountCommand;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.identity.application.result.CreateStudentAccountResult;
import com.eduhub.edu_hub.identity.application.result.StudentAccountInfo;
import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.Role;
import com.eduhub.edu_hub.identity.domain.model.User;
import com.eduhub.edu_hub.identity.domain.repository.RefreshTokenRepository;
import com.eduhub.edu_hub.identity.domain.repository.RoleRepository;
import com.eduhub.edu_hub.identity.domain.repository.UserRepository;
import com.eduhub.edu_hub.notification.application.command.AccountEmailCommand;
import com.eduhub.edu_hub.notification.application.command.AdmissionEmailCommand;
import com.eduhub.edu_hub.notification.application.facade.NotificationFacade;
import com.eduhub.edu_hub.notification.domain.enums.EmailTemplateType;
import com.eduhub.edu_hub.shared.enums.RoleName;
import com.eduhub.edu_hub.shared.exception.BaseException;
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
    private final NotificationFacade.Mail notifications;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    @Transactional
    public Long createLecturerAccountWithoutNotification(CreateLecturerAccountCommand command) {
        if (users.existsByUsername(command.username())) {
            throw new BaseException(ErrorCode.DUPLICATE_LECTURER_CODE);
        }
        Role role = roles.findByRoleNameAndDeletedFalse(RoleName.LECTURER.name())
                .orElseThrow(() -> new BaseException(ErrorCode.VALIDATION_ERROR));
        return save(command.username(), command.email(), command.initialPassword(), role, true)
                .getId();
    }

    @Override
    @Transactional(readOnly = true)
    public CreateStudentAccountResult sendLecturerAccountNotification(Long userId, String initialPassword) {

        User account = user(userId);

        // Kiểm tra loại tài khoản nhận thông báo, không phải quyền của người gọi.
        if (account.isDeleted()
                || !RoleName.LECTURER.name().equals(account.getRole().getRoleName())
                || account.getStatus() != UserStatus.ACTIVE) {
            return new CreateStudentAccountResult(userId, false, "ACCOUNT_NOT_AVAILABLE");
        }

        if (!encoder.matches(initialPassword, account.getPasswordHash())) {
            return new CreateStudentAccountResult(userId, false, "INITIAL_PASSWORD_NO_LONGER_VALID");
        }

        return sendStudentAccountNotification(userId, initialPassword);
    }

    @Override
    @Transactional
    public CreateStudentAccountResult createStudentAccount(CreateStudentAccountCommand command) {
        Long userId = createStudentAccountRecord(command);
        return sendStudentAccountNotification(userId, command.initialPassword());
    }

    @Override
    @Transactional
    public Long createStudentAccountWithoutNotification(CreateStudentAccountCommand command) {
        return createStudentAccountRecord(command);
    }

    @Override
    public CreateStudentAccountResult sendStudentAccountNotification(Long userId, String initialPassword) {
        User user = user(userId);
        if (!user.isFirstLogin()) {
            return new CreateStudentAccountResult(userId, false, "ACCOUNT_ALREADY_ACTIVATED");
        }
        if (user.isDeleted()
                || user.getStatus() != UserStatus.ACTIVE
                || (user.getLockedUntil() != null
                        && user.getLockedUntil().isAfter(LocalDateTime.now(java.time.ZoneOffset.UTC)))) {
            return new CreateStudentAccountResult(userId, false, "ACCOUNT_NOT_AVAILABLE");
        }
        if (!encoder.matches(initialPassword, user.getPasswordHash())) {
            return new CreateStudentAccountResult(userId, false, "INITIAL_PASSWORD_NO_LONGER_VALID");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return new CreateStudentAccountResult(userId, false, "ACCOUNT_EMAIL_MISSING");
        }
        var delivery = notifications.sendAccount(AccountEmailCommand.builder()
                .recipient(user.getEmail())
                .username(user.getUsername())
                .temporaryPassword(initialPassword)
                .templateType(EmailTemplateType.ACCOUNT_CREATED)
                .build());
        if (!delivery.sent()) {
            log.error("Initial account email failed for user {}", userId);
        }
        return new CreateStudentAccountResult(userId, delivery.sent(), delivery.error());
    }

    @Override
    @Transactional(readOnly = true)
    public UserInfoResponse requireCurrentUser(String accessToken) {

        TokenService.TokenClaims claims = tokens.validateAccessToken(accessToken);

        if (claims == null) throw new BaseException(ErrorCode.UNAUTHENTICATED);
        User current = users.findByUsernameAndDeletedFalse(claims.username())
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHENTICATED));

        LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);

        boolean validSession = sessions.findByIdAndRevokedFalse(claims.sessionId())
                .filter(session -> session.getUser().getId().equals(current.getId()))
                .filter(session -> now.isBefore(session.getExpiresAt()))
                .isPresent();

        if (!current.getId().equals(claims.userId())
                || current.getStatus() != UserStatus.ACTIVE
                || (current.getLockedUntil() != null && now.isBefore(current.getLockedUntil()))
                || !validSession) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED);
        }

        return response(current);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentAccountInfo getStudentAccount(Long userId) {
        User account = user(userId);
        if (account.isDeleted()
                || !RoleName.STUDENT.name().equals(account.getRole().getRoleName())) {
            throw new BaseException(ErrorCode.USER_NOT_FOUND);
        }
        boolean locked = account.getStatus() != UserStatus.ACTIVE
                || (account.getLockedUntil() != null
                        && account.getLockedUntil().isAfter(LocalDateTime.now(java.time.ZoneOffset.UTC)));
        return new StudentAccountInfo(
                account.getId(),
                account.getUsername(),
                account.getEmail(),
                account.getStatus().name(),
                account.isFirstLogin(),
                locked);
    }

    @Override
    public CreateStudentAccountResult sendStudentAdmissionNotification(Long userId, String recipient) {
        var account = getStudentAccount(userId);
        if (recipient == null || recipient.isBlank()) {
            return new CreateStudentAccountResult(userId, false, "ACCOUNT_EMAIL_MISSING");
        }
        var delivery = notifications.sendAdmissionConfirmation(AdmissionEmailCommand.builder()
                .recipient(recipient)
                .username(account.username())
                .build());
        if (!delivery.sent()) {
            log.error("Admission confirmation email failed for user {}", userId);
        }
        return new CreateStudentAccountResult(userId, delivery.sent(), delivery.error());
    }

    private Long createStudentAccountRecord(CreateStudentAccountCommand command) {

        if (users.existsByUsername(command.username())) {
            throw new BaseException(ErrorCode.DUPLICATE_STUDENT_CODE);
        }

        Role role = roles.findByRoleNameAndDeletedFalse(RoleName.STUDENT.name())
                .orElseThrow(() -> new BaseException(ErrorCode.VALIDATION_ERROR));

        return save(
                        command.username(),
                        command.email(),
                        command.initialPassword(),
                        role,
                        command.requirePasswordChange())
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
        return users.findById(id).orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
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
