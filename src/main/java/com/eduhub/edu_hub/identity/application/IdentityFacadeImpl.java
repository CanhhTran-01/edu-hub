package com.eduhub.edu_hub.identity.application;

import com.eduhub.edu_hub.identity.api.dto.UserInfoResponse;
import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.Role;
import com.eduhub.edu_hub.identity.domain.model.User;
import com.eduhub.edu_hub.identity.domain.repository.RefreshTokenRepository;
import com.eduhub.edu_hub.identity.domain.repository.RoleRepository;
import com.eduhub.edu_hub.identity.domain.repository.UserRepository;
import com.eduhub.edu_hub.shared.enums.RoleName;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
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
    private final TemporaryPasswordMailService mailService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    @Transactional
    public Long createLecturerAccountWithoutNotification(CreateLecturerAccountCommand command) {
        if (users.existsByUsername(command.username())) {
            throw new BaseException(ErrorCode.DUPLICATE_LECTURER_CODE);
        }
        Role role = roles.findByRoleNameAndDeletedFalse(RoleName.LECTURER.name())
                .orElseThrow(() -> new BaseException(ErrorCode.VALIDATION_ERROR));
        return save(command.username(), command.email(), command.initialPassword(), role, true).getId();
    }

    @Override
    @Transactional(readOnly = true)
    public CreateStudentAccountResult sendLecturerAccountNotification(Long userId, String initialPassword) {
        User account = user(userId);
        if (account.isDeleted() || !RoleName.LECTURER.name().equals(account.getRole().getRoleName())
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
    public Long createUser(String username, String rawPassword, String roleName, boolean isFirstLogin) {
        Role role = roles.findByRoleNameAndDeletedFalse(roleName)
                .orElseThrow(() -> new BaseException(ErrorCode.VALIDATION_ERROR));
        return save(username, null, rawPassword, role, isFirstLogin).getId();
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
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return new CreateStudentAccountResult(userId, false, "ACCOUNT_EMAIL_MISSING");
        }
        try {
            mailService.sendAccount(user.getEmail(), user.getUsername(), initialPassword);
            return new CreateStudentAccountResult(userId, true, null);
        } catch (MailException exception) {
            log.error("Initial account email failed for user {}", userId);
            return new CreateStudentAccountResult(userId, false, "EMAIL_SEND_FAILED");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public String requireAcademicAdmin(String accessToken) {
        TokenService.TokenClaims claims = tokens.validateAccessToken(accessToken);
        if (claims == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED);
        }
        User actor = users.findByUsernameAndDeletedFalse(claims.username())
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHENTICATED));
        if (!actor.getId().equals(claims.userId())
                || actor.getStatus() != UserStatus.ACTIVE
                || sessions.findByIdAndRevokedFalse(claims.sessionId()).stream()
                        .noneMatch(session -> session.getUser().getId().equals(actor.getId()))) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED);
        }
        String role = actor.getRole().getRoleName();
        if (!RoleName.ACADEMIC_ADMIN.name().equals(role)
                && !RoleName.SYSTEM_ADMIN.name().equals(role)) {
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        }
        return actor.getUsername();
    }

    @Override
    @Transactional(readOnly = true)
    public UserInfoResponse requireCurrentStudent(String accessToken) {
        TokenService.TokenClaims claims = tokens.validateAccessToken(accessToken);
        if (claims == null) throw new BaseException(ErrorCode.UNAUTHENTICATED);
        User current = users.findByUsernameAndDeletedFalse(claims.username())
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHENTICATED));
        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        boolean validSession = sessions.findByIdAndRevokedFalse(claims.sessionId())
                .filter(session -> session.getUser().getId().equals(current.getId()))
                .filter(session -> now.isBefore(session.getExpiresAt())).isPresent();
        if (!current.getId().equals(claims.userId()) || current.getStatus() != UserStatus.ACTIVE
                || (current.getLockedUntil() != null && now.isBefore(current.getLockedUntil())) || !validSession) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED);
        }
        if (!RoleName.STUDENT.name().equals(current.getRole().getRoleName()))
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        return response(current);
    }

    private Long createStudentAccountRecord(CreateStudentAccountCommand command) {
        if (users.existsByUsername(command.username())) {
            throw new BaseException(ErrorCode.DUPLICATE_STUDENT_CODE);
        }
        Role role = roles.findByRoleNameAndDeletedFalse(RoleName.STUDENT.name())
                .orElseThrow(() -> new BaseException(ErrorCode.VALIDATION_ERROR));
        return save(command.username(), command.email(), command.initialPassword(), role,
                command.requirePasswordChange()).getId();
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

    @Override public void lockUser(Long userId, String reason) { throw new UnsupportedOperationException(); }
    @Override public void unlockUser(Long userId) { throw new UnsupportedOperationException(); }
    @Override
    @Transactional
    public void disableUser(Long userId) {
        // UC-10: tham gia transaction của People; DISABLED chặn đăng nhập mới.
        User account = user(userId);
        account.setStatus(UserStatus.DISABLED);
        // Thu hồi mọi phiên đang mở để token cũ không tiếp tục truy cập API.
        sessions.findByUser_IdAndRevokedFalse(userId).forEach(session -> session.setRevoked(true));
    }

    @Override
    @Transactional
    public void enableUser(Long userId) {
        // UC-10: mở lại đăng nhập và xóa khóa tạm do nhập sai mật khẩu.
        // Giữ nguyên mật khẩu/firstLogin; các phiên bị thu hồi không được phục hồi.
        User account = user(userId);
        account.setStatus(UserStatus.ACTIVE);
        account.setLockedUntil(null);
        account.setFailedLoginCount(0);
    }
    @Override public String resetPassword(Long userId) { throw new UnsupportedOperationException(); }
    @Override public Optional<UserInfoResponse> findById(Long userId) { return users.findById(userId).map(this::response); }
    @Override public Optional<UserInfoResponse> findByUsername(String username) { return users.findByUsernameAndDeletedFalse(username).map(this::response); }
    @Override public boolean existsByUsername(String username) { return users.existsByUsername(username); }

    private User user(Long id) { return users.findById(id).orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND)); }
    private UserInfoResponse response(User user) {
        return UserInfoResponse.builder().id(user.getId()).username(user.getUsername()).email(user.getEmail())
                .role(user.getRole().getRoleName()).status(user.getStatus().name())
                .isFirstLogin(user.isFirstLogin()).build();
    }
}
