package com.eduhub.edu_hub.identity.application.service;

import static com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey.*;

import com.eduhub.edu_hub.identity.api.dto.request.ChangePasswordRequest;
import com.eduhub.edu_hub.identity.api.dto.request.LoginRequest;
import com.eduhub.edu_hub.identity.api.dto.request.RefreshTokenRequest;
import com.eduhub.edu_hub.identity.api.dto.response.LoginResponse;
import com.eduhub.edu_hub.identity.api.dto.response.RefreshTokenResponse;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.exception.IdentityAuthenticationException;
import com.eduhub.edu_hub.identity.domain.enums.RoleName;
import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.RefreshToken;
import com.eduhub.edu_hub.identity.domain.model.User;
import com.eduhub.edu_hub.identity.domain.policy.PasswordPolicy;
import com.eduhub.edu_hub.identity.domain.repository.RefreshTokenRepository;
import com.eduhub.edu_hub.identity.domain.repository.UserRepository;
import com.eduhub.edu_hub.identity.infrastructure.security.CustomUserDetail;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.systemconfig.api.dto.response.SystemConfigSnapshotResponse;
import com.eduhub.edu_hub.shared.systemconfig.application.service.SystemConfigService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class AuthApplicationService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenService tokenService;
    private final PasswordPolicy passwordPolicy;
    private final PlatformTransactionManager transactionManager;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SystemConfigService systemConfigService;
    private final PeopleFacade.LoginProfile loginProfiles;
    private final Clock clock;

    private record LoginAttempt(LoginResponse response, ErrorCode error) {}

    private record RefreshAttempt(RefreshTokenResponse response, ErrorCode error) {}

    private LoginAttempt authenticate(LoginRequest request) {

        var authenticationConfiguration = systemConfigService.getSnapshot();

        CustomUserDetail principal;
        try {
            var authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.getUsername(), request.getPassword()));
            principal = (CustomUserDetail) authentication.getPrincipal();

        } catch (BadCredentialsException exception) {

            // UserDetailsService đã khóa User trong cùng transaction, username không tồn tại không có bộ đếm để ghi.
            userRepository.findByUsernameForUpdate(request.getUsername()).ifPresent(user -> {
                LocalDateTime now = LocalDateTime.now(clock);
                clearExpiredTemporaryLock(user, now);
                recordFailedAttempt(user, now, authenticationConfiguration);
            });
            return new LoginAttempt(null, ErrorCode.INVALID_CREDENTIALS);

        } catch (LockedException | DisabledException exception) {
            return new LoginAttempt(null, ErrorCode.ACCOUNT_LOCKED);

        } catch (AuthenticationException exception) {
            // Sự cố provider/dịch vụ xác thực là lỗi hệ thống, không được ghi nhận thành một lần sai mật khẩu.
            throw new IdentityAuthenticationException(ErrorCode.UNEXPECTED_ERROR, exception);
        }

        User user = userRepository
                .findById(Objects.requireNonNull(principal).getUserId())
                .orElseThrow(() -> new IdentityAuthenticationException(ErrorCode.UNEXPECTED_ERROR));

        Instant issuedAt = clock.instant();
        LocalDateTime now = LocalDateTime.ofInstant(issuedAt, clock.getZone());
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        return new LoginAttempt(createLoginResponse(user, principal, issuedAt, now, authenticationConfiguration), null);
    }

    public LoginResponse login(LoginRequest request) {

        // Không dùng @Transactional cho authenticate() vì self-invocation
        // Lưu lỗi đăng nhập trong transaction rồi mới ném ra ngoài để không rollback bộ đếm thất bại.
        LoginAttempt attempt = new TransactionTemplate(transactionManager).execute(status -> authenticate(request));

        if (attempt == null) throw new IdentityAuthenticationException(ErrorCode.UNEXPECTED_ERROR);
        if (attempt.error() != null) throw new IdentityAuthenticationException(attempt.error());

        return attempt.response();
    }

    public RefreshTokenResponse refreshTokens(RefreshTokenRequest request) {
        if (request == null
                || request.getRefreshToken() == null
                || request.getRefreshToken().isBlank()) {
            throw new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED);
        }
        String tokenHash = tokenService.hash(request.getRefreshToken());
        RefreshAttempt attempt = new TransactionTemplate(transactionManager)
                .execute(status -> rotateRefreshTokenInTransaction(tokenHash));

        // Thu hồi do dùng lại token phải commit trước khi trả 401, không ném lỗi trong callback để rollback việc thu
        // hồi.
        if (attempt == null) throw new IdentityAuthenticationException(ErrorCode.UNEXPECTED_ERROR);
        if (attempt.error() != null) throw new IdentityAuthenticationException(attempt.error());
        return attempt.response();
    }

    private RefreshAttempt rotateRefreshTokenInTransaction(String tokenHash) {
        Long userId = refreshTokenRepository
                .findUserIdByTokenHash(tokenHash)
                .orElseThrow(() -> new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED));

        // Khóa User trước token, cùng thứ tự với đổi/reset mật khẩu và logout; đọc lại token sau khi chờ khóa.
        User user = userRepository
                .findByIdForUpdate(userId)
                .orElseThrow(() -> new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED));
        RefreshToken currentToken = refreshTokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED));
        Instant issuedAt = clock.instant();
        LocalDateTime now = LocalDateTime.ofInstant(issuedAt, clock.getZone());
        if (!currentToken.getUser().getId().equals(userId)
                || currentToken.isDeleted()
                || !now.isBefore(currentToken.getExpiresAt())) {
            throw new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED);
        }
        if (currentToken.isRevoked()) {
            refreshTokenRepository
                    .findByTokenFamilyIdAndRevokedFalse(currentToken.getTokenFamilyId())
                    .forEach(token -> token.setRevoked(true));
            return new RefreshAttempt(null, ErrorCode.UNAUTHENTICATED);
        }
        if (user.isDeleted()
                || user.getStatus() != UserStatus.ACTIVE
                || (user.getLockedUntil() != null && now.isBefore(user.getLockedUntil()))) {
            throw new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED);
        }

        var authenticationConfiguration = systemConfigService.getSnapshot();
        String refreshToken = tokenService.newRefreshToken();
        RefreshToken nextToken = new RefreshToken();
        nextToken.setUser(user);
        nextToken.setTokenHash(tokenService.hash(refreshToken));
        nextToken.setTokenFamilyId(currentToken.getTokenFamilyId());
        nextToken.setExpiresAt(currentToken.getExpiresAt());
        currentToken.setRevoked(true);
        nextToken = refreshTokenRepository.saveAndFlush(nextToken);

        // Không kéo dài phiên khi xoay token; JWT mới cũng không vượt hạn phiên còn lại.
        Instant expiresAt = issuedAt.plus(authenticationConfiguration.getDuration(ACCESS_TOKEN_LIFETIME));
        Instant sessionExpiresAt =
                currentToken.getExpiresAt().atZone(clock.getZone()).toInstant();
        if (expiresAt.isAfter(sessionExpiresAt)) expiresAt = sessionExpiresAt;
        return new RefreshAttempt(
                RefreshTokenResponse.builder()
                        .accessToken(tokenService.generateAccessToken(user, nextToken.getId(), issuedAt, expiresAt))
                        .refreshToken(refreshToken)
                        .expiresIn(Duration.between(issuedAt, expiresAt).toSeconds())
                        // TokenService đọc firstLogin/role hiện tại trong DB; refresh không nâng quyền JWT hạn chế.
                        .requirePasswordChange(user.isFirstLogin())
                        .build(),
                null);
    }

    public void logout(Jwt authenticatedJwt, String refreshToken) {

        TokenService.TokenClaims claims = tokenService.requireAuthenticatedClaims(authenticatedJwt);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            userRepository
                    .findByIdIncludingDeletedForUpdate(claims.userId())
                    .orElseThrow(() -> new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED));
            RefreshToken token = refreshTokenRepository
                    .findByTokenHashForUpdate(tokenService.hash(refreshToken))
                    .orElseThrow(() -> new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED));

            if (!LocalDateTime.now(clock).isBefore(token.getExpiresAt())) {
                throw new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED);
            }
            if (!token.getId().equals(claims.sessionId())
                    || !token.getUser().getId().equals(claims.userId())) {
                throw new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED);
            }

            token.setRevoked(true);
            refreshTokenRepository.save(token);
            // Logout bằng cặp token cũ còn hạn cũng chấm dứt các token đã xoay trong cùng phiên, không ảnh hưởng phiên
            // khác.
            refreshTokenRepository
                    .findByTokenFamilyIdAndRevokedFalse(token.getTokenFamilyId())
                    .forEach(session -> session.setRevoked(true));

            // TODO(ACCESS_TOKEN_REVOCATION): Cân nhắc phát jti và lưu jti bị thu hồi vào Redis với TTL còn lại;
            //  kiểm tra danh sách thu hồi trong xác thực JWT.
            //  Hiện tại logout chỉ revoke refresh token, access token còn hạn vẫn hợp lệ.
        });
    }

    public void changePassword(Jwt authenticatedJwt, ChangePasswordRequest request) {

        TokenService.TokenClaims claims = tokenService.requireAuthenticatedClaims(authenticatedJwt);

        // Giữ khóa User đến khi cập nhật mật khẩu, lịch sử và mọi refresh token hoàn tất.
        // Lỗi sai mật khẩu được trả khỏi callback để commit bộ đếm trước khi trả lỗi cho client.
        ErrorCode error = new TransactionTemplate(transactionManager).execute(status -> {
            return updatePasswordInTransaction(claims, request);
        });

        if (error != null) throw new IdentityAuthenticationException(error);
    }

    private void clearExpiredTemporaryLock(User user, LocalDateTime now) {
        if (user.getLockedUntil() != null && !now.isBefore(user.getLockedUntil())) {
            user.setLockedUntil(null);
            user.setFailedLoginCount(0);
        }
    }

    private void recordFailedAttempt(
            User user, LocalDateTime now, SystemConfigSnapshotResponse authenticationConfiguration) {
        int failedAttempts = user.getFailedLoginCount() + 1;

        user.setFailedLoginCount(failedAttempts);

        if (failedAttempts >= authenticationConfiguration.getInteger(AUTHENTICATION_MAXIMUM_FAILED_LOGINS)) {
            user.setLockedUntil(
                    now.plus(authenticationConfiguration.getDuration(AUTHENTICATION_TEMPORARY_LOCK_DURATION)));
        }
        userRepository.save(user);
    }

    private LoginResponse createLoginResponse(
            User user,
            CustomUserDetail principal,
            Instant issuedAt,
            LocalDateTime now,
            SystemConfigSnapshotResponse authenticationConfiguration) {
        String fullName = findLoginProfileFullName(user, principal);

        // JWT giữ Instant tuyệt đối; không gắn UTC vào một LocalDateTime đang mang giờ Việt Nam.
        String refreshToken = tokenService.newRefreshToken();

        RefreshToken session = new RefreshToken();
        session.setUser(user);
        session.setTokenHash(tokenService.hash(refreshToken));
        session.setExpiresAt(now.plus(authenticationConfiguration.getDuration(REFRESH_TOKEN_LIFETIME)));
        session = refreshTokenRepository.saveAndFlush(session);

        // API refresh đọc lại firstLogin trong DB để giữ JWT hạn chế, không bỏ qua bước đổi mật khẩu.

        UserInfoResponse info = UserInfoResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(principal.getRole())
                .fullName(fullName)
                .isFirstLogin(user.isFirstLogin())
                .status(user.getStatus().name())
                .build();

        LoginResponse response = LoginResponse.builder()
                .accessToken(tokenService.generateAccessToken(
                        user,
                        session.getId(),
                        issuedAt,
                        issuedAt.plus(authenticationConfiguration.getDuration(ACCESS_TOKEN_LIFETIME))))
                .refreshToken(refreshToken)
                .expiresIn(authenticationConfiguration
                        .getDuration(ACCESS_TOKEN_LIFETIME)
                        .toSeconds())
                .user(info)
                .requirePasswordChange(user.isFirstLogin())
                .build();

        return response;
    }

    private String findLoginProfileFullName(User user, CustomUserDetail principal) {
        // Chỉ tải loại hồ sơ tương ứng sau khi xác thực thành công; thiếu hồ sơ vẫn được đăng nhập.
        String fullName =
                switch (RoleName.valueOf(principal.getRole())) {
                    case STUDENT -> loginProfiles
                            .findStudentFullNameByUserId(user.getId())
                            .orElse(null);
                    case LECTURER -> loginProfiles
                            .findLecturerFullNameByUserId(user.getId())
                            .orElse(null);
                    case ACADEMIC_ADMIN, SYSTEM_ADMIN -> null;
                };

        return fullName;
    }

    private ErrorCode updatePasswordInTransaction(TokenService.TokenClaims claims, ChangePasswordRequest request) {
        var authenticationConfiguration = systemConfigService.getSnapshot();
        User user = userRepository
                .findByIdForUpdate(claims.userId())
                .orElseThrow(() -> new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED));

        LocalDateTime now = LocalDateTime.now(clock);

        boolean hasValidSession = refreshTokenRepository
                .findByIdAndRevokedFalse(claims.sessionId())
                .filter(session -> session.getUser().getId().equals(user.getId()))
                .filter(session -> now.isBefore(session.getExpiresAt()))
                .isPresent();

        if (!user.getUsername().equals(claims.username()) || !hasValidSession) return ErrorCode.UNAUTHENTICATED;
        if (user.getStatus() != UserStatus.ACTIVE) return ErrorCode.ACCOUNT_LOCKED;
        if (user.getLockedUntil() != null && now.isBefore(user.getLockedUntil())) return ErrorCode.ACCOUNT_LOCKED;

        clearExpiredTemporaryLock(user, now);

        // người dùng có nhập đúng mật khẩu hiện tại hay không, ngoại trừ lần đăng nhập đầu tiên
        if (!user.isFirstLogin()) {
            passwordPolicy.validateCurrentPasswordInput(request.getCurrentPassword());
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
                recordFailedAttempt(user, now, authenticationConfiguration);
                return ErrorCode.INVALID_CREDENTIALS;
            }
        }

        validateNewPassword(user, request, authenticationConfiguration);

        // Mật khẩu tạm sau cấp/reset không được đẩy vào lịch sử mật khẩu chính thức.
        if (!user.isFirstLogin()) user.setPreviousPasswordHash(user.getPasswordHash());
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(now);
        user.setFirstLogin(false);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);

        userRepository.save(user);

        // thu hồi toàn bộ refresh token còn hiệu lực của một user
        refreshTokenRepository.findByUser_IdAndRevokedFalse(user.getId()).forEach(session -> session.setRevoked(true));

        return null;

        // NOTE: Thu hồi cả refresh token hiện tại và các phiên khác; FE phải xóa hai token và đăng nhập lại.
        // Access token đã phát vẫn hợp lệ đến exp theo quyết định hiện tại, kể cả JWT hạn chế first-login;
        // endpoint có kiểm tra phiên DB riêng có thể từ chối ngay vì refresh token đã bị revoke.

        // TODO(ACCESS_TOKEN_REVOCATION): Khi đổi chính sách cần thu hồi access token tức thì, phát jti và
        //  kiểm tra danh sách thu hồi Redis với TTL còn lại trong JwtDecoder; chưa triển khai ở use-case này.
    }

    private void validateNewPassword(
            User user, ChangePasswordRequest request, SystemConfigSnapshotResponse authenticationConfiguration) {
        // Kiểm tra giới hạn byte trước khi gọi BCrypt.
        // first-login chỉ bỏ qua mật khẩu hiện tại, không bỏ policy.
        passwordPolicy.validate(
                request.getNewPassword(),
                request.getConfirmPassword(),
                user.getUsername(),
                authenticationConfiguration.getInteger(PASSWORD_MINIMUM_LENGTH));
        passwordPolicy.validatePasswordReuse(
                passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash()),
                user.getPreviousPasswordHash() != null
                        && passwordEncoder.matches(request.getNewPassword(), user.getPreviousPasswordHash()));
    }
}
