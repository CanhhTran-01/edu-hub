package com.eduhub.edu_hub.identity.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.identity.api.dto.request.ChangePasswordRequest;
import com.eduhub.edu_hub.identity.api.dto.request.LoginRequest;
import com.eduhub.edu_hub.identity.api.dto.request.RefreshTokenRequest;
import com.eduhub.edu_hub.identity.application.exception.IdentityAuthenticationException;
import com.eduhub.edu_hub.identity.application.exception.PasswordValidationException;
import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.RefreshToken;
import com.eduhub.edu_hub.identity.domain.model.Role;
import com.eduhub.edu_hub.identity.domain.model.User;
import com.eduhub.edu_hub.identity.domain.policy.PasswordPolicy;
import com.eduhub.edu_hub.identity.domain.repository.RefreshTokenRepository;
import com.eduhub.edu_hub.identity.domain.repository.UserRepository;
import com.eduhub.edu_hub.identity.infrastructure.security.CustomUserDetail;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.shared.config.TimeConfig;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.systemconfig.SystemConfigTestData;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class AuthApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-18T17:30:00Z"),
            new TimeConfig().applicationClock().getZone());
    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final JwtEncoder jwtEncoder = mock(JwtEncoder.class);
    private final TokenService tokens = new TokenService(jwtEncoder);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final PasswordEncoder passwordEncoder = spy(new BCryptPasswordEncoder(4));
    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final com.eduhub.edu_hub.shared.systemconfig.application.service.SystemConfigService systemConfigService =
            SystemConfigTestData.configuredService();
    private final AuthApplicationService service = new AuthApplicationService(
            users,
            refreshTokens,
            tokens,
            new PasswordPolicy(),
            transactionManager,
            passwordEncoder,
            authenticationManager,
            systemConfigService,
            mock(PeopleFacade.LoginProfile.class),
            CLOCK);
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

    @Test
    void loginStoresVietnamSessionExpiryAndKeepsJwtInstantUnshifted() {
        Role role = new Role();
        role.setRoleName("SYSTEM_ADMIN");
        User account = new User();
        account.setId(7L);
        account.setUsername("account");
        account.setRole(role);
        account.setStatus(UserStatus.ACTIVE);
        when(authenticationManager.authenticate(any()))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated(
                        new CustomUserDetail(account, CLOCK), null, List.of()));
        when(users.findById(7L)).thenReturn(Optional.of(account));
        when(refreshTokens.saveAndFlush(any())).thenAnswer(invocation -> {
            RefreshToken session = invocation.getArgument(0);
            session.setId(9L);
            return session;
        });
        when(jwtEncoder.encode(any()))
                .thenReturn(Jwt.withTokenValue("encoded-jwt")
                        .header("alg", "HS256")
                        .subject("account")
                        .build());
        LoginRequest request = new LoginRequest();
        request.setUsername("account");
        request.setPassword("unit-password");

        service.login(request);

        var savedSession = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).saveAndFlush(savedSession.capture());
        assertThat(savedSession.getValue().getExpiresAt()).isEqualTo(LocalDateTime.parse("2026-11-26T00:30:00"));
        var tokenParameters = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(tokenParameters.capture());
        assertThat(tokenParameters.getValue().getClaims().getIssuedAt()).isEqualTo(CLOCK.instant());
        assertThat(tokenParameters.getValue().getClaims().getExpiresAt())
                .isEqualTo(CLOCK.instant().plus(Duration.ofMinutes(15)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void logoutRevokesMatchingRefreshTokenWithoutDecodingJwtAgain(boolean alreadyRevoked) {
        RefreshToken session = session(7L, 9L);
        session.setRevoked(alreadyRevoked);
        allowLogout(session);
        service.logout(authenticatedJwt, "refresh-token");
        assertThat(session.isRevoked()).isTrue();
        verify(refreshTokens).save(session);
        verify(transactionManager).commit(any());
        var lockOrder = inOrder(users, refreshTokens);
        lockOrder.verify(users).findByIdIncludingDeletedForUpdate(7L);
        lockOrder.verify(refreshTokens).findByTokenHashForUpdate(anyString());
        verifyNoInteractions(jwtEncoder);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void logoutRejectsRefreshTokenOfAnotherUserOrSession(boolean differentUser) {
        RefreshToken session = differentUser ? session(8L, 9L) : session(7L, 10L);
        allowLogout(session);
        assertThatThrownBy(() -> service.logout(authenticatedJwt, "refresh-token"))
                .isInstanceOfSatisfying(
                        IdentityAuthenticationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        assertThat(session.isRevoked()).isFalse();
        verify(refreshTokens, never()).save(any());
        verify(transactionManager).rollback(any());
        verifyNoInteractions(jwtEncoder);
    }

    @Test
    void logoutRetainsExistingRefreshExpiryCheck() {
        RefreshToken session = session(7L, 9L);
        session.setExpiresAt(LocalDateTime.now(CLOCK).minusMinutes(1));
        allowLogout(session);
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
        verifyNoInteractions(refreshTokens, users, jwtEncoder);
        verify(transactionManager, never()).getTransaction(any());
    }

    @Test
    void changePasswordWithoutPrincipalStillRequiresAuthentication() {
        assertThatThrownBy(() -> service.changePassword(null, new ChangePasswordRequest()))
                .isInstanceOfSatisfying(
                        IdentityAuthenticationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        verifyNoInteractions(users, refreshTokens, jwtEncoder);
        verify(transactionManager, never()).getTransaction(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void refreshRotatesTheSecretAndReadsCurrentFirstLoginAndRole(boolean firstLogin) {
        RefreshToken currentToken = allowRefresh(firstLogin);
        LocalDateTime originalExpiry = currentToken.getExpiresAt();

        var response = service.refreshTokens(refreshRequest());

        var nextToken = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).saveAndFlush(nextToken.capture());
        assertThat(currentToken.isRevoked()).isTrue();
        assertThat(nextToken.getValue().getId()).isEqualTo(10L);
        assertThat(nextToken.getValue().getTokenFamilyId()).isEqualTo(currentToken.getTokenFamilyId());
        assertThat(nextToken.getValue().getExpiresAt()).isEqualTo(originalExpiry);
        assertThat(nextToken.getValue().isRevoked()).isFalse();
        assertThat(response.getRefreshToken()).isNotEqualTo("refresh-token");
        assertThat(nextToken.getValue().getTokenHash()).isEqualTo(tokens.hash(response.getRefreshToken()));
        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(900);
        assertThat(response.isRequirePasswordChange()).isEqualTo(firstLogin);
        var parameters = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(parameters.capture());
        var claims = parameters.getValue().getClaims();
        assertThat(claims.<Long>getClaim("sid")).isEqualTo(10L);
        assertThat(claims.<Long>getClaim("uid")).isEqualTo(7L);
        assertThat(claims.getClaimAsString("token_use")).isEqualTo(firstLogin ? "PASSWORD_CHANGE" : "ACCESS");
        assertThat(claims.getClaimAsString("role")).isEqualTo(firstLogin ? null : "LECTURER");
        assertThat(claims.getIssuedAt()).isEqualTo(CLOCK.instant());
        var lockOrder = inOrder(refreshTokens, users);
        lockOrder.verify(refreshTokens).findUserIdByTokenHash(tokens.hash("refresh-token"));
        lockOrder.verify(users).findByIdForUpdate(7L);
        lockOrder.verify(refreshTokens).findByTokenHashForUpdate(tokens.hash("refresh-token"));
        verify(transactionManager).commit(any());
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void refreshCapsJwtExpiryAtTheRemainingAbsoluteSessionLifetime() {
        RefreshToken currentToken = allowRefresh(false);
        currentToken.setExpiresAt(LocalDateTime.now(CLOCK).plusSeconds(40));
        var response = service.refreshTokens(refreshRequest());
        assertThat(response.getExpiresIn()).isEqualTo(40);
        var parameters = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(parameters.capture());
        assertThat(parameters.getValue().getClaims().getExpiresAt())
                .isEqualTo(CLOCK.instant().plusSeconds(40));
    }

    @Test
    void reusingRevokedTokenCommitsFamilyRevocationBeforeReturningUnauthorized() {
        RefreshToken previousToken = allowRefresh(false);
        previousToken.setRevoked(true);
        RefreshToken activeToken = session(7L, 10L);
        activeToken.setTokenFamilyId(previousToken.getTokenFamilyId());
        when(refreshTokens.findByTokenFamilyIdAndRevokedFalse(previousToken.getTokenFamilyId()))
                .thenReturn(List.of(activeToken));

        assertThatThrownBy(() -> service.refreshTokens(refreshRequest()))
                .isInstanceOfSatisfying(
                        IdentityAuthenticationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));

        assertThat(activeToken.isRevoked()).isTrue();
        verify(refreshTokens, never()).findByUser_IdAndRevokedFalse(any());
        verify(refreshTokens, never()).saveAndFlush(any());
        verify(transactionManager).commit(any());
        verify(transactionManager, never()).rollback(any());
        verifyNoInteractions(jwtEncoder);
    }

    @ParameterizedTest
    @CsvSource({"-1", "0"})
    void refreshRejectsExpiredTokensIncludingTheExactExpiryBoundary(long secondsUntilExpiry) {
        RefreshToken currentToken = allowRefresh(false);
        currentToken.setExpiresAt(LocalDateTime.now(CLOCK).plusSeconds(secondsUntilExpiry));
        assertThatThrownBy(() -> service.refreshTokens(refreshRequest()))
                .isInstanceOf(IdentityAuthenticationException.class);
        assertThat(currentToken.isRevoked()).isFalse();
        verify(refreshTokens, never()).saveAndFlush(any());
        verifyNoInteractions(jwtEncoder);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOCKED", "DISABLED", "DELETED", "TEMPORARILY_LOCKED"})
    void refreshRejectsUnavailableAccounts(String accountState) {
        RefreshToken currentToken = allowRefresh(false);
        User account = currentToken.getUser();
        switch (accountState) {
            case "DELETED" -> account.setDeleted(true);
            case "TEMPORARILY_LOCKED" -> account.setLockedUntil(
                    LocalDateTime.now(CLOCK).plusMinutes(1));
            default -> account.setStatus(UserStatus.valueOf(accountState));
        }
        assertThatThrownBy(() -> service.refreshTokens(refreshRequest()))
                .isInstanceOf(IdentityAuthenticationException.class);
        assertThat(currentToken.isRevoked()).isFalse();
        verify(refreshTokens, never()).saveAndFlush(any());
        verifyNoInteractions(jwtEncoder);
    }

    @Test
    void refreshRejectsUnknownTokenBeforeLockingAnyUser() {
        assertThatThrownBy(() -> service.refreshTokens(refreshRequest()))
                .isInstanceOf(IdentityAuthenticationException.class);
        verifyNoInteractions(users, jwtEncoder);
    }

    @Test
    void refreshRejectsMissingRequestAndBlankSecretBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service.refreshTokens(null)).isInstanceOf(IdentityAuthenticationException.class);
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(" ");
        assertThatThrownBy(() -> service.refreshTokens(request)).isInstanceOf(IdentityAuthenticationException.class);
        verifyNoInteractions(users, refreshTokens, jwtEncoder, transactionManager);
    }

    @Test
    void jwtIssuingFailureRollsBackRotationAndIsNotReportedAsInvalidCredentials() {
        allowRefresh(false);
        when(jwtEncoder.encode(any()))
                .thenThrow(new org.springframework.security.oauth2.jwt.JwtEncodingException("failed"));
        assertThatThrownBy(() -> service.refreshTokens(refreshRequest()))
                .isInstanceOfSatisfying(
                        IdentityAuthenticationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNEXPECTED_ERROR));
        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void logoutWithAnOlderPairAlsoRevokesTheRotatedTokenInThatFamily() {
        RefreshToken previousToken = session(7L, 9L);
        previousToken.setRevoked(true);
        allowLogout(previousToken);
        RefreshToken activeToken = session(7L, 10L);
        activeToken.setTokenFamilyId(previousToken.getTokenFamilyId());
        when(refreshTokens.findByTokenFamilyIdAndRevokedFalse(previousToken.getTokenFamilyId()))
                .thenReturn(List.of(activeToken));
        service.logout(authenticatedJwt, "refresh-token");
        assertThat(activeToken.isRevoked()).isTrue();
        verify(refreshTokens, never()).findByUser_IdAndRevokedFalse(any());
    }

    private RefreshTokenRequest refreshRequest() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh-token");
        return request;
    }

    private RefreshToken allowRefresh(boolean firstLogin) {
        RefreshToken currentToken = session(7L, 9L);
        currentToken.getUser().setUsername("account");
        currentToken.getUser().setStatus(UserStatus.ACTIVE);
        currentToken.getUser().setFirstLogin(firstLogin);
        Role currentRole = new Role();
        currentRole.setRoleName("LECTURER");
        currentToken.getUser().setRole(currentRole);
        when(refreshTokens.findUserIdByTokenHash(tokens.hash("refresh-token"))).thenReturn(Optional.of(7L));
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(currentToken.getUser()));
        when(refreshTokens.findByTokenHashForUpdate(tokens.hash("refresh-token")))
                .thenReturn(Optional.of(currentToken));
        when(refreshTokens.saveAndFlush(any())).thenAnswer(invocation -> {
            RefreshToken nextToken = invocation.getArgument(0);
            nextToken.setId(10L);
            return nextToken;
        });
        when(jwtEncoder.encode(any()))
                .thenReturn(Jwt.withTokenValue("new-access-token")
                        .header("alg", "HS256")
                        .subject("account")
                        .build());
        return currentToken;
    }

    private void allowLogout(RefreshToken session) {
        User account = new User();
        account.setId(7L);
        when(users.findByIdIncludingDeletedForUpdate(7L)).thenReturn(Optional.of(account));
        when(refreshTokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(session));
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
        verifyNoInteractions(jwtEncoder);
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
        assertThat(account.getLockedUntil()).isEqualTo(LocalDateTime.parse("2026-11-19T00:45:00"));
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
    void appliesChangedFailureThresholdAndLockDurationWithoutRecreatingService() {
        User account = allowPasswordChange();
        assertThatThrownBy(() -> service.changePassword(authenticatedJwt, passwordChange("wrong", "NextPass2!")))
                .isInstanceOf(IdentityAuthenticationException.class);
        assertThat(account.getLockedUntil()).isNull();
        when(systemConfigService.getSnapshot())
                .thenReturn(SystemConfigTestData.snapshot(java.util.Map.of(
                        com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey
                                        .AUTHENTICATION_MAXIMUM_FAILED_LOGINS,
                                "2",
                        com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey
                                        .AUTHENTICATION_TEMPORARY_LOCK_DURATION,
                                "5m")));
        assertThatThrownBy(() -> service.changePassword(authenticatedJwt, passwordChange("wrong", "NextPass2!")))
                .isInstanceOf(IdentityAuthenticationException.class);
        assertThat(account.getFailedLoginCount()).isEqualTo(2);
        assertThat(account.getLockedUntil()).isEqualTo(LocalDateTime.now(CLOCK).plusMinutes(5));
    }

    @Test
    void usesCurrentPasswordMinimumForEachChangeWithoutRecreatingPolicy() {
        User account = allowPasswordChange();
        when(systemConfigService.getSnapshot())
                .thenReturn(SystemConfigTestData.snapshot(java.util.Map.of(
                        com.eduhub.edu_hub.shared.systemconfig.domain.enums.SystemConfigKey.PASSWORD_MINIMUM_LENGTH,
                        "12")));
        assertThatThrownBy(
                        () -> service.changePassword(authenticatedJwt, passwordChange("CurrentPass1!", "NextPass2!")))
                .isInstanceOf(PasswordValidationException.class);
        when(systemConfigService.getSnapshot()).thenReturn(SystemConfigTestData.snapshot(java.util.Map.of()));
        service.changePassword(authenticatedJwt, passwordChange("CurrentPass1!", "NextPass2!"));
        assertThat(passwordEncoder.matches("NextPass2!", account.getPasswordHash()))
                .isTrue();
    }

    @Test
    void expiredTemporaryLockClearsCounterBeforeRecordingTheNextWrongPassword() {
        User account = allowPasswordChange();
        account.setFailedLoginCount(5);
        account.setLockedUntil(LocalDateTime.now(CLOCK).minusSeconds(1));
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
            session.setExpiresAt(LocalDateTime.now(CLOCK).minusSeconds(1));
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
        session.setExpiresAt(LocalDateTime.now(CLOCK).plusDays(1));
        return session;
    }
}
