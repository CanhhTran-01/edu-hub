package com.eduhub.edu_hub.identity.application.service;

import com.eduhub.edu_hub.identity.api.dto.request.LockUserRequest;
import com.eduhub.edu_hub.identity.api.dto.request.SearchUserAccountsRequest;
import com.eduhub.edu_hub.identity.api.dto.response.UserAccountDetailResponse;
import com.eduhub.edu_hub.identity.api.dto.response.UserAccountSummaryResponse;
import com.eduhub.edu_hub.identity.application.exception.IdentityAccountException;
import com.eduhub.edu_hub.identity.application.exception.IdentityConfigurationException;
import com.eduhub.edu_hub.identity.application.specification.UserAccountSpecification;
import com.eduhub.edu_hub.identity.domain.enums.RoleName;
import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.User;
import com.eduhub.edu_hub.identity.domain.policy.UserAccountAdministrationPolicy;
import com.eduhub.edu_hub.identity.domain.repository.RefreshTokenRepository;
import com.eduhub.edu_hub.identity.domain.repository.RoleRepository;
import com.eduhub.edu_hub.identity.domain.repository.UserRepository;
import com.eduhub.edu_hub.notification.api.dto.request.SendAccountEmailRequest;
import com.eduhub.edu_hub.notification.application.facade.NotificationFacade;
import com.eduhub.edu_hub.notification.domain.enums.EmailTemplateType;
import com.eduhub.edu_hub.people.api.dto.response.AccountProfileContactResponse;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.mapper.PageMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RoleRepository roleRepository;
    private final CurrentUserAuthenticationService currentUserAuthenticationService;
    private final TokenService tokenService;
    private final NotificationFacade.ImmediateEmail immediateEmailNotifications;
    private final PasswordEncoder passwordEncoder;
    private final PeopleFacade.AccountProfile accountProfiles;

    @Transactional(readOnly = true)
    public PageResponse<UserAccountSummaryResponse> searchUserAccounts(
            Jwt authenticatedJwt, SearchUserAccountsRequest request) {
        if (request.getPage() < 0 || request.getSize() < 1) {
            throw new IdentityAccountException(ErrorCode.VALIDATION_ERROR);
        }

        currentUserAuthenticationService.requireCurrentUser(authenticatedJwt);

        Pageable pageable = PageRequest.of(
                request.getPage(), request.getSize(), Sort.by("id").ascending());

        Specification<User> accountCriteria = Specification.allOf(
                UserAccountSpecification.notDeleted(),
                UserAccountSpecification.matchesKeyword(request.getKeyword()),
                UserAccountSpecification.hasRole(request.getRole()),
                UserAccountSpecification.hasStatus(request.getStatus()),
                UserAccountSpecification.requiresPasswordChange(request.getFirstLogin()));
        Page<UserAccountSummaryResponse> userAccounts =
                userRepository.findAll(accountCriteria, pageable).map(this::toUserAccountSummaryResponse);

        return PageMapper.toPageResponse(userAccounts);
    }

    @Transactional(readOnly = true)
    public UserAccountDetailResponse getUserAccountDetail(Jwt authenticatedJwt, Long userId) {
        currentUserAuthenticationService.requireCurrentUser(authenticatedJwt);

        User targetUser = userRepository
                .findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new IdentityAccountException(ErrorCode.USER_NOT_FOUND));

        // Chỉ đọc loại hồ sơ phù hợp role theo User ID; thiếu hồ sơ không làm mất thông tin tài khoản.
        Optional<AccountProfileContactResponse> profileContact = findAccountProfileContact(targetUser);
        LocalDateTime evaluatedAt = LocalDateTime.now(ZoneOffset.UTC);

        return UserAccountDetailResponse.builder()
                .id(targetUser.getId())
                .username(targetUser.getUsername())
                .role(targetUser.getRole().getRoleName())
                .status(targetUser.getStatus().name())
                .failedLoginCount(targetUser.getFailedLoginCount())
                .lockedUntil(targetUser.getLockedUntil())
                .passwordChangedAt(targetUser.getPasswordChangedAt())
                .createdAt(targetUser.getCreatedAt())
                .updatedAt(targetUser.getUpdatedAt())
                .email(targetUser.getEmail())
                .firstLogin(targetUser.isFirstLogin())
                // Khóa tạm độc lập với LOCKED thủ công; mốc khóa đã hết hạn vẫn được giữ nguyên để đọc.
                .temporarilyLocked(
                        targetUser.getLockedUntil() != null && evaluatedAt.isBefore(targetUser.getLockedUntil()))
                .fullName(profileContact
                        .map(AccountProfileContactResponse::fullName)
                        .orElse(null))
                .phone(profileContact.map(AccountProfileContactResponse::phone).orElse(null))
                .build();
    }

    @Transactional
    public void lockUserAccount(Jwt authenticatedJwt, Long userId, LockUserRequest request) {

        tokenService.requireAuthenticatedClaims(authenticatedJwt);
        UserAccountAdministrationPolicy.validateLockReason(request.reason());

        lockSystemAdminStatusChanges();

        AccountAdministrationContext accountContext = requireAccountAdministrationContext(authenticatedJwt, userId);
        User actorUser = accountContext.actorUser();
        User targetUser = accountContext.targetUser();
        UserAccountAdministrationPolicy.validateTargetAccountStatus(targetUser.getStatus());

        // ACTIVE/chưa xóa là định nghĩa admin đang hoạt động; khóa tạm không làm giảm phép đếm này.
        long activeSystemAdminCount = userRepository.countByDeletedFalseAndStatusAndRole_RoleName(
                UserStatus.ACTIVE, RoleName.SYSTEM_ADMIN.name());
        UserAccountAdministrationPolicy.validateSelfLock(
                actorUser.getId(), actorUser.getRole().getRoleName(), targetUser.getId(), activeSystemAdminCount);

        targetUser.setStatus(UserStatus.LOCKED);
        revokeUserSessions(targetUser.getId());
        // NOTE: Khóa tài khoản cũng chưa thu hồi JWT ACCESS tức thì tại API chỉ kiểm tra chữ ký/hạn token.

        // TODO(ACCESS_TOKEN_REVOCATION): Đồng bộ lock với logout/reset/đổi mật khẩu khi triển khai thu hồi jti.

        // TODO(AUDIT_LOG): Lưu actor, tài khoản đích và lý do khóa sau commit khi có hạ tầng audit.
        log.info(
                "Khóa tài khoản: actorUserId={}, targetUserId={}, reason={}",
                actorUser.getId(),
                userId,
                request.reason());
    }

    @Transactional
    public void unlockUserAccount(Jwt authenticatedJwt, Long userId) {

        tokenService.requireAuthenticatedClaims(authenticatedJwt);
        lockSystemAdminStatusChanges();

        AccountAdministrationContext accountContext = requireAccountAdministrationContext(authenticatedJwt, userId);
        User targetUser = accountContext.targetUser();
        UserAccountAdministrationPolicy.validateTargetAccountStatus(targetUser.getStatus());

        // ACTIVE vẫn cần gỡ khóa tạm do đăng nhập sai; không khôi phục bất kỳ phiên đã revoke nào.
        targetUser.setStatus(UserStatus.ACTIVE);
        targetUser.setLockedUntil(null);
        targetUser.setFailedLoginCount(0);

        // TODO(AUDIT_LOG): Lưu actor, tài khoản đích và trạng thái trước/sau mở khóa sau commit khi bật audit.
        log.info(
                "Mở khóa tài khoản: actorUserId={}, targetUserId={}",
                accountContext.actorUser().getId(),
                userId);
    }

    @Transactional
    public void resetUserPassword(Jwt authenticatedJwt, Long userId) {

        AccountAdministrationContext accountContext = requireAccountAdministrationContext(authenticatedJwt, userId);
        User targetUser = accountContext.targetUser();
        UserAccountAdministrationPolicy.validateTargetAccountStatus(targetUser.getStatus());

        if (targetUser.getEmail() == null || targetUser.getEmail().isBlank()) {
            throw new IdentityAccountException(ErrorCode.ACCOUNT_EMAIL_MISSING);
        }

        String temporaryPassword = tokenService.generateTemporaryPassword();

        // Khóa cùng User như đổi mật khẩu để không ghi đè lịch sử khi hai thao tác chạy đồng thời.
        // Reset liên tiếp chỉ thay mật khẩu tạm, vẫn giữ mật khẩu chính thức gần nhất.
        if (!targetUser.isFirstLogin()) targetUser.setPreviousPasswordHash(targetUser.getPasswordHash());
        targetUser.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        targetUser.setFirstLogin(true);
        targetUser.setPasswordChangedAt(LocalDateTime.now(ZoneOffset.UTC));
        // Reset gỡ khóa tạm để dùng mật khẩu mới, nhưng giữ nguyên LOCKED nếu đã bị khóa thủ công.
        targetUser.setLockedUntil(null);
        targetUser.setFailedLoginCount(0);

        // Chỉ revoke refresh token; access token đã phát vẫn được Resource Server chấp nhận đến exp.
        revokeUserSessions(targetUser.getId());

        // TODO(ACCESS_TOKEN_REVOCATION): Đồng bộ với đổi mật khẩu/logout khi triển khai thu hồi jti bằng Redis;
        //  hiện không kiểm tra phiên DB trên mọi API nên chưa bảo đảm kết thúc ngay toàn bộ truy cập cũ.

        var emailDelivery = immediateEmailNotifications.sendAccountEmail(SendAccountEmailRequest.builder()
                .recipient(targetUser.getEmail())
                .username(targetUser.getUsername())
                .temporaryPassword(temporaryPassword)
                .templateType(EmailTemplateType.PASSWORD_RESET)
                .build());
        if (!emailDelivery.sent()) {
            log.error("Gửi email reset mật khẩu thất bại: targetUserId={}", userId);
            throw new IdentityAccountException(ErrorCode.PASSWORD_EMAIL_FAILED);
            // NOTE: Gửi đồng bộ trong transaction; lỗi gửi/render phải rollback mật khẩu, lịch sử và revoke phiên.
        }

        // TODO(NOTIFICATION_DELIVERY): Khi có cơ chế giao nhận phù hợp, xử lý trường hợp SMTP đã gửi nhưng
        //  transaction commit thất bại; rollback DB hiện không thể thu hồi email đã gửi. Chưa chuyển reset sang queue.

        // TODO(AUDIT_LOG): Lưu actor, tài khoản đích và kết quả reset sau commit; không lưu mật khẩu/hash/nội dung thư.
        log.info(
                "Reset mật khẩu: actorUserId={}, targetUserId={}",
                accountContext.actorUser().getId(),
                userId);
    }

    private void lockSystemAdminStatusChanges() {

        // Khóa chung trước User, giữ đến hết transaction để hai admin không cùng đếm 2 rồi cùng tự khóa.
        // TODO: Mọi UC vô hiệu hóa/xóa/đổi role SYSTEM_ADMIN bổ sung sau này phải tuân theo cùng khóa này.

        roleRepository
                .findByRoleNameForUpdate(RoleName.SYSTEM_ADMIN.name())
                .orElseThrow(() -> new IdentityConfigurationException(
                        "Thiếu vai trò SYSTEM_ADMIN để bảo vệ thao tác thay đổi trạng thái tài khoản"));
    }

    private AccountAdministrationContext requireAccountAdministrationContext(Jwt authenticatedJwt, Long userId) {
        // Chặn principal thiếu/sai claims trước truy vấn, đây không phải giải mã lại JWT.
        tokenService.requireAuthenticatedClaims(authenticatedJwt);

        // tải lên tài khoản cần lock và tài khoản actor
        Optional<User> lockedTargetUser = userRepository.findByIdForUpdate(userId);
        User actorUser = currentUserAuthenticationService.requireCurrentUser(authenticatedJwt);

        // TODO(AUTHORIZATION): Bổ sung phân quyền quản trị tài khoản bằng Spring Security.

        User targetUser = lockedTargetUser
                .filter(account -> !account.isDeleted())
                .orElseThrow(() -> new IdentityAccountException(ErrorCode.USER_NOT_FOUND));

        return new AccountAdministrationContext(actorUser, targetUser);
    }

    private void revokeUserSessions(Long userId) {
        refreshTokenRepository.findByUser_IdAndRevokedFalse(userId).forEach(session -> session.setRevoked(true));
    }

    private Optional<AccountProfileContactResponse> findAccountProfileContact(User user) {
        if (RoleName.STUDENT.name().equals(user.getRole().getRoleName())) {
            return accountProfiles.findStudentContactByUserId(user.getId());
        }
        if (RoleName.LECTURER.name().equals(user.getRole().getRoleName())) {
            return accountProfiles.findLecturerContactByUserId(user.getId());
        }
        return Optional.empty();
    }

    private UserAccountSummaryResponse toUserAccountSummaryResponse(User user) {
        return UserAccountSummaryResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole().getRoleName())
                .status(user.getStatus().name())
                .build();
    }

    private record AccountAdministrationContext(User actorUser, User targetUser) {}
}
