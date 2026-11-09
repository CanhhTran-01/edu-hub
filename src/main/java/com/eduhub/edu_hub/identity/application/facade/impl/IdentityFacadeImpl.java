package com.eduhub.edu_hub.identity.application.facade.impl;

import com.eduhub.edu_hub.identity.api.dto.request.CreateLecturerAccountRequest;
import com.eduhub.edu_hub.identity.api.dto.request.CreateStudentAccountRequest;
import com.eduhub.edu_hub.identity.api.dto.response.AccountNotificationResponse;
import com.eduhub.edu_hub.identity.api.dto.response.StudentAccountResponse;
import com.eduhub.edu_hub.identity.api.dto.response.UserAccountEmailResponse;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.exception.IdentityAccountException;
import com.eduhub.edu_hub.identity.application.exception.IdentityAuthenticationException;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.identity.application.service.CurrentUserAuthenticationService;
import com.eduhub.edu_hub.identity.domain.enums.RoleName;
import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.Role;
import com.eduhub.edu_hub.identity.domain.model.User;
import com.eduhub.edu_hub.identity.domain.repository.RoleRepository;
import com.eduhub.edu_hub.identity.domain.repository.UserRepository;
import com.eduhub.edu_hub.identity.infrastructure.config.PasswordSettings;
import com.eduhub.edu_hub.notification.api.dto.request.SendAccountEmailRequest;
import com.eduhub.edu_hub.notification.api.dto.request.SendAdmissionConfirmationEmailRequest;
import com.eduhub.edu_hub.notification.application.facade.NotificationFacade;
import com.eduhub.edu_hub.notification.domain.enums.EmailTemplateType;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.text.TextsNormalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdentityFacadeImpl implements IdentityFacade {
    private static final int ACCOUNT_EMAIL_QUERY_BATCH_SIZE = 500;
    private final UserRepository users;
    private final RoleRepository roles;
    private final CurrentUserAuthenticationService currentUserAuthenticationService;
    private final NotificationFacade.ImmediateEmail immediateEmailNotifications;
    private final PasswordEncoder encoder;
    private final PasswordSettings passwordSettings;

    @Override
    @Transactional
    public Long createLecturerAccountWithoutNotification(CreateLecturerAccountRequest request) {
        if (users.existsByUsername(request.username())) {
            throw new IdentityAccountException(ErrorCode.DUPLICATE_LECTURER_CODE);
        }
        Role role = roles.findByRoleNameAndDeletedFalse(RoleName.LECTURER.name())
                .orElseThrow(() -> new IdentityAccountException(ErrorCode.VALIDATION_ERROR));
        return saveInitialAccount(request.username(), request.email(), role).getId();
    }

    @Override
    @Transactional(readOnly = true)
    public AccountNotificationResponse sendLecturerAccountNotification(Long userId) {

        User account = user(userId);

        // Kiểm tra loại tài khoản nhận thông báo, không phải quyền của người gọi.
        if (account.isDeleted()
                || !RoleName.LECTURER.name().equals(account.getRole().getRoleName())
                || account.getStatus() != UserStatus.ACTIVE) {
            return new AccountNotificationResponse(userId, false, "ACCOUNT_NOT_AVAILABLE");
        }

        if (!encoder.matches(passwordSettings.getInitialPassword(), account.getPasswordHash())) {
            return new AccountNotificationResponse(userId, false, "INITIAL_PASSWORD_NO_LONGER_VALID");
        }

        return sendStudentAccountNotification(userId);
    }

    @Override
    @Transactional
    public Long createStudentAccountWithoutNotification(CreateStudentAccountRequest request) {
        return createStudentAccountRecord(request);
    }

    @Override
    public AccountNotificationResponse sendStudentAccountNotification(Long userId) {

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

        if (!encoder.matches(passwordSettings.getInitialPassword(), user.getPasswordHash())) {
            return new AccountNotificationResponse(userId, false, "INITIAL_PASSWORD_NO_LONGER_VALID");
        }

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return new AccountNotificationResponse(userId, false, "ACCOUNT_EMAIL_MISSING");
        }

        var delivery = immediateEmailNotifications.sendAccountEmail(SendAccountEmailRequest.builder()
                .recipient(user.getEmail())
                .username(user.getUsername())
                .temporaryPassword(passwordSettings.getInitialPassword())
                .templateType(EmailTemplateType.ACCOUNT_CREATED)
                .build());

        if (!delivery.sent()) log.error("Initial account email failed for user {}", userId);
        return new AccountNotificationResponse(userId, delivery.sent(), delivery.error());
    }

    @Override
    @Transactional(readOnly = true)
    public UserInfoResponse requireCurrentUser(Jwt authenticatedJwt) {
        return response(currentUserAuthenticationService.requireCurrentUser(authenticatedJwt));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void updateAccountEmail(Long userId, String email) {

        // People xác định User ID từ principal hoặc liên kết hồ sơ đã tải, không nhận User ID tùy chọn từ body.
        // Với PATCH /me, khóa User sau Student; mọi thay đổi vẫn thuộc transaction hồ sơ do consumer mở.
        User account = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED));

        String normalizedEmail = TextsNormalizer.normalizeEmail(email);
        if (!Objects.equals(normalizedEmail, account.getEmail())) {
            account.setEmail(normalizedEmail);
            users.save(account);

            // TODO(AUDIT_LOG): Khi bật audit, ghi thay đổi email sau commit cùng cập nhật hồ sơ cá nhân.
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserAccountEmailResponse> findAccountEmailsByUserIds(Set<Long> userIds) {
        if (userIds.isEmpty()) return List.of();
        List<Long> sortedUserIds =
                userIds.stream().filter(Objects::nonNull).sorted().toList();
        List<UserAccountEmailResponse> accountEmails = new ArrayList<>();
        // Hồ sơ và tài khoản có vòng đời riêng; không lọc trạng thái/xóa mềm User khi đọc email để hiển thị.
        for (int startIndex = 0; startIndex < sortedUserIds.size(); startIndex += ACCOUNT_EMAIL_QUERY_BATCH_SIZE) {
            int endIndex = Math.min(startIndex + ACCOUNT_EMAIL_QUERY_BATCH_SIZE, sortedUserIds.size());
            accountEmails.addAll(
                    users.findAccountEmailsByUserIds(new HashSet<>(sortedUserIds.subList(startIndex, endIndex))));
        }
        accountEmails.sort(Comparator.comparing(UserAccountEmailResponse::userId));
        return List.copyOf(accountEmails);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> findUserIdsByEmail(String email) {
        String normalizedEmail = TextsNormalizer.normalizeEmail(email);
        return normalizedEmail == null ? Set.of() : users.findUserIdsByEmail(normalizedEmail);
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
                SendAdmissionConfirmationEmailRequest.builder()
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

        return saveInitialAccount(request.username(), request.email(), role).getId();
    }

    private User saveInitialAccount(String username, String email, Role role) {
        User user = new User();

        user.setUsername(username);
        user.setEmail(email);
        // Identity sở hữu nguồn mật khẩu khởi tạo; consumer không được tùy chọn bỏ qua first-login.
        user.setPasswordHash(encoder.encode(passwordSettings.getInitialPassword()));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setFirstLogin(true);

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
