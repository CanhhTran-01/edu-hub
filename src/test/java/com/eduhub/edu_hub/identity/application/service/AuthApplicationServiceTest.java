package com.eduhub.edu_hub.identity.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.identity.api.dto.request.ChangePasswordRequest;
import com.eduhub.edu_hub.identity.api.dto.request.LoginRequest;
import com.eduhub.edu_hub.identity.application.exception.IdentityAuthenticationException;
import com.eduhub.edu_hub.identity.application.exception.PasswordValidationException;
import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.RefreshToken;
import com.eduhub.edu_hub.identity.domain.model.User;
import com.eduhub.edu_hub.identity.domain.policy.PasswordPolicy;
import com.eduhub.edu_hub.identity.domain.repository.RefreshTokenRepository;
import com.eduhub.edu_hub.identity.domain.repository.UserRepository;
import com.eduhub.edu_hub.identity.infrastructure.config.AuthenticationSettings;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class AuthApplicationServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final JwtDecoder jwtDecoder = mock(JwtDecoder.class);
    private final JwtEncoder jwtEncoder = mock(JwtEncoder.class);
    private final TokenService tokens = new TokenService(jwtEncoder, jwtDecoder);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final PasswordEncoder passwordEncoder = spy(new BCryptPasswordEncoder(4));
    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final AuthApplicationService service = new AuthApplicationService(
            users,
            refreshTokens,
            tokens,
            new PasswordPolicy(8),
            transactionManager,
            passwordEncoder,
            authenticationManager,
            new AuthenticationSettings(5, Duration.ofMinutes(15), Duration.ofMinutes(15), Duration.ofDays(7)),
            mock(PeopleFacade.LoginProfile.class));
    private final Jwt authenticatedJwt = Jwt.withTokenValue("unit-token")
            .header("alg", "HS256")
            .subject("account")
            .claim("uid", 7L)
            .claim("sid", 9L)
            .build();

    @BeforeEach
    void transactions() {
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void logoutRevokesMatchingRefreshTokenWithoutDecodingJwtAgain(boolean alreadyRevoked) {
        RefreshToken session = session(7L, 9L);
        session.setRevoked(alreadyRevoked);
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.of(session));
        service.logout(authenticatedJwt, "refresh-token");
        assertThat(session.isRevoked()).isTrue();
        verify(refreshTokens).save(session);
        verify(transactionManager).commit(any());
        verifyNoInteractions(jwtDecoder, jwtEncoder, users);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void logoutRejectsRefreshTokenOfAnotherUserOrSession(boolean differentUser) {
        RefreshToken session = differentUser ? session(8L, 9L) : session(7L, 10L);
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.of(session));
        assertThatThrownBy(() -> service.logout(authenticatedJwt, "refresh-token"))
                .isInstanceOfSatisfying(
                        IdentityAuthenticationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        assertThat(session.isRevoked()).isFalse();
        verify(refreshTokens, never()).save(any());
        verify(transactionManager).rollback(any());
        verifyNoInteractions(jwtDecoder, jwtEncoder);
    }

    @Test
    void logoutRetainsExistingRefreshExpiryCheck() {
        RefreshToken session = session(7L, 9L);
        session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.of(session));
        assertThatThrownBy(() -> service.logout(authenticatedJwt, "refresh-token"))
                .isInstanceOf(IdentityAuthenticationException.class);
        assertThat(session.isRevoked()).isFalse();
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void logoutWithoutPrincipalFailsBeforeTransactionOrDatabaseAccess() {
        assertThatThrownBy(() -> service.logout(null, "refresh-token"))
                .isInstanceOfSatisfying(
                        IdentityAuthenticationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        verifyNoInteractions(refreshTokens, users, jwtDecoder, jwtEncoder);
        verify(transactionManager, never()).getTransaction(any());
    }

    @Test
    void changePasswordWithoutPrincipalStillRequiresAuthentication() {
        assertThatThrownBy(() -> service.changePassword(null, new ChangePasswordRequest()))
                .isInstanceOfSatisfying(
                        IdentityAuthenticationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        verifyNoInteractions(users, refreshTokens, jwtDecoder, jwtEncoder);
        verify(transactionManager, never()).getTransaction(any());
    }

    @Test
    void normalChangeKeepsOnePreviousOfficialPasswordAndRevokesEveryRefreshToken() {
        User account = allowPasswordChange();
        String currentHash = account.getPasswordHash();
        account.setPreviousPasswordHash(passwordEncoder.encode("OlderPass1!"));
        var currentSession = session(7L, 9L);
        var anotherSession = session(7L, 10L);
        when(refreshTokens.findByUser_IdAndRevokedFalse(7L)).thenReturn(List.of(currentSession, anotherSession));

        service.changePassword(authenticatedJwt, passwordChange("CurrentPass1!", "NextPass2!"));

        assertThat(account.getPreviousPasswordHash()).isEqualTo(currentHash);
        assertThat(passwordEncoder.matches("NextPass2!", account.getPasswordHash()))
                .isTrue();
        assertThat(account.isFirstLogin()).isFalse();
        assertThat(account.getPasswordChangedAt()).isNotNull();
        assertThat(account.getFailedLoginCount()).isZero();
        assertThat(currentSession.isRevoked()).isTrue();
        assertThat(anotherSession.isRevoked()).isTrue();
        verify(transactionManager).commit(any());
        verifyNoInteractions(jwtDecoder, jwtEncoder);
    }

    @Test
    void firstChangeSkipsCurrentPasswordAndDoesNotStoreTemporaryHashAsOfficialHistory() {
        User account = allowPasswordChange();
        account.setFirstLogin(true);
        account.setFailedLoginCount(2);
        service.changePassword(authenticatedJwt, passwordChange("ignored-current-password", "NextPass2!"));
        assertThat(account.isFirstLogin()).isFalse();
        assertThat(account.getPreviousPasswordHash()).isNull();
        assertThat(account.getFailedLoginCount()).isZero();
        verify(passwordEncoder, never()).matches(eq("ignored-current-password"), anyString());
    }

    @Test
    void firstChangeAfterResetPreservesThePreviousOfficialPassword() {
        User account = allowPasswordChange();
        account.setFirstLogin(true);
        String previousOfficialHash = passwordEncoder.encode("PriorPass1!");
        account.setPreviousPasswordHash(previousOfficialHash);
        service.changePassword(authenticatedJwt, passwordChange(null, "NextPass2!"));
        assertThat(account.getPreviousPasswordHash()).isEqualTo(previousOfficialHash);
        assertThat(passwordEncoder.matches("NextPass2!", account.getPasswordHash()))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsThePreviousOfficialPasswordBeforeAndAfterReset(boolean firstLogin) {
        User account = allowPasswordChange();
        account.setFirstLogin(firstLogin);
        account.setPreviousPasswordHash(passwordEncoder.encode("PriorPass1!"));
        String currentHash = account.getPasswordHash();
        assertThatThrownBy(
                        () -> service.changePassword(authenticatedJwt, passwordChange("CurrentPass1!", "PriorPass1!")))
                .isInstanceOf(PasswordValidationException.class);
        assertThat(account.getPasswordHash()).isEqualTo(currentHash);
        assertThat(account.getFailedLoginCount()).isZero();
        verify(refreshTokens, never()).findByUser_IdAndRevokedFalse(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsTheCurrentPasswordEvenDuringFirstLogin(boolean firstLogin) {
        User account = allowPasswordChange();
        account.setFirstLogin(firstLogin);
        assertThatThrownBy(() ->
                        service.changePassword(authenticatedJwt, passwordChange("CurrentPass1!", "CurrentPass1!")))
                .isInstanceOf(PasswordValidationException.class);
        assertThat(account.getFailedLoginCount()).isZero();
    }

    @Test
    void allowsAnOlderInitialPasswordOutsideTheTwoRetainedHashes() {
        User account = allowPasswordChange();
        account.setPreviousPasswordHash(passwordEncoder.encode("PriorPass1!"));
        service.changePassword(authenticatedJwt, passwordChange("CurrentPass1!", "InitialPass1!"));
        assertThat(passwordEncoder.matches("InitialPass1!", account.getPasswordHash()))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void weakNewPasswordIsRejectedWithoutCountingAFailedAttempt(boolean firstLogin) {
        User account = allowPasswordChange();
        account.setFirstLogin(firstLogin);
        assertThatThrownBy(() -> service.changePassword(authenticatedJwt, passwordChange("CurrentPass1!", "weak")))
                .isInstanceOf(PasswordValidationException.class);
        assertThat(account.getFailedLoginCount()).isZero();
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void missingCurrentPasswordIsValidationRatherThanABruteForceAttempt() {
        User account = allowPasswordChange();
        assertThatThrownBy(() -> service.changePassword(authenticatedJwt, passwordChange(null, "NextPass2!")))
                .isInstanceOfSatisfying(
                        PasswordValidationException.class, exception -> assertThat(exception.getErrors())
                                .extracting("field")
                                .containsExactly("currentPassword"));
        assertThat(account.getFailedLoginCount()).isZero();
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void oversizedNewPasswordIsRejectedBeforeBcryptMatchesOrEncoding() {
        User account = allowPasswordChange();
        account.setFirstLogin(true);
        assertThatThrownBy(
                        () -> service.changePassword(authenticatedJwt, passwordChange(null, "Aa1!" + "á".repeat(35))))
                .isInstanceOf(PasswordValidationException.class);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void wrongCurrentPasswordCommitsSharedCounterAndLocksAtTheConfiguredThreshold() {
        User account = allowPasswordChange();
        account.setFailedLoginCount(4);
        assertThatThrownBy(() -> service.changePassword(authenticatedJwt, passwordChange("wrong", "NextPass2!")))
                .isInstanceOfSatisfying(
                        IdentityAuthenticationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
        assertThat(account.getFailedLoginCount()).isEqualTo(5);
        assertThat(account.getLockedUntil())
                .isAfter(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(14));
        verify(transactionManager).commit(any());
        verify(transactionManager, never()).rollback(any());

        assertThatThrownBy(
                        () -> service.changePassword(authenticatedJwt, passwordChange("CurrentPass1!", "NextPass2!")))
                .isInstanceOfSatisfying(
                        IdentityAuthenticationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED));
        assertThat(account.getFailedLoginCount()).isEqualTo(5);
    }

    @Test
    void loginAndPasswordChangeShareTheFailureCounter() {
        User account = allowPasswordChange();
        account.setFailedLoginCount(3);
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("invalid"));
        when(users.findByUsernameForUpdate("account")).thenReturn(Optional.of(account));
        LoginRequest login = new LoginRequest();
        login.setUsername("account");
        login.setPassword("wrong");
        assertThatThrownBy(() -> service.login(login)).isInstanceOf(IdentityAuthenticationException.class);
        assertThat(account.getFailedLoginCount()).isEqualTo(4);
        assertThatThrownBy(() -> service.changePassword(authenticatedJwt, passwordChange("wrong", "NextPass2!")))
                .isInstanceOf(IdentityAuthenticationException.class);
        assertThat(account.getFailedLoginCount()).isEqualTo(5);
        assertThat(account.getLockedUntil()).isNotNull();
        verify(transactionManager, times(2)).commit(any());
    }

    @Test
    void expiredTemporaryLockClearsCounterBeforeRecordingTheNextWrongPassword() {
        User account = allowPasswordChange();
        account.setFailedLoginCount(5);
        account.setLockedUntil(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        assertThatThrownBy(() -> service.changePassword(authenticatedJwt, passwordChange("wrong", "NextPass2!")))
                .isInstanceOf(IdentityAuthenticationException.class);
        assertThat(account.getFailedLoginCount()).isEqualTo(1);
        assertThat(account.getLockedUntil()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"expired", "revoked", "differentUser"})
    void rejectsAnInvalidRefreshSessionBeforePasswordVerification(String invalidSession) {
        allowPasswordChange();
        RefreshToken session = session(invalidSession.equals("differentUser") ? 8L : 7L, 9L);
        if (invalidSession.equals("expired"))
            session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        when(refreshTokens.findByIdAndRevokedFalse(9L))
                .thenReturn(invalidSession.equals("revoked") ? Optional.empty() : Optional.of(session));
        assertThatThrownBy(
                        () -> service.changePassword(authenticatedJwt, passwordChange("CurrentPass1!", "NextPass2!")))
                .isInstanceOfSatisfying(
                        IdentityAuthenticationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        verifyNoInteractions(passwordEncoder);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOCKED", "DISABLED"})
    void unavailableAccountsCannotChangePassword(String status) {
        User account = allowPasswordChange();
        account.setStatus(UserStatus.valueOf(status));
        assertThatThrownBy(
                        () -> service.changePassword(authenticatedJwt, passwordChange("CurrentPass1!", "NextPass2!")))
                .isInstanceOfSatisfying(
                        IdentityAuthenticationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED));
        verifyNoInteractions(passwordEncoder);
    }

    private User allowPasswordChange() {
        User account = new User();
        account.setId(7L);
        account.setUsername("account");
        account.setStatus(UserStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode("CurrentPass1!"));
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(account));
        when(refreshTokens.findByIdAndRevokedFalse(9L)).thenReturn(Optional.of(session(7L, 9L)));
        clearInvocations(passwordEncoder);
        return account;
    }

    private ChangePasswordRequest passwordChange(String currentPassword, String newPassword) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword(currentPassword);
        request.setNewPassword(newPassword);
        request.setConfirmPassword(newPassword);
        return request;
    }

    private RefreshToken session(Long userId, Long sessionId) {
        User account = new User();
        account.setId(userId);
        RefreshToken session = new RefreshToken();
        session.setId(sessionId);
        session.setUser(account);
        session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
        return session;
    }
}
