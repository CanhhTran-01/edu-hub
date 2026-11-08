package com.eduhub.edu_hub.identity.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.identity.api.dto.request.ChangePasswordRequest;
import com.eduhub.edu_hub.identity.application.exception.IdentityAuthenticationException;
import com.eduhub.edu_hub.identity.domain.model.RefreshToken;
import com.eduhub.edu_hub.identity.domain.model.User;
import com.eduhub.edu_hub.identity.domain.policy.PasswordPolicyValidator;
import com.eduhub.edu_hub.identity.domain.repository.RefreshTokenRepository;
import com.eduhub.edu_hub.identity.domain.repository.UserRepository;
import com.eduhub.edu_hub.identity.infrastructure.config.AuthenticationSettings;
import com.eduhub.edu_hub.people.application.facade.PeopleFacade;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.AuthenticationManager;
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
    private final AuthApplicationService service = new AuthApplicationService(
            users,
            refreshTokens,
            tokens,
            mock(PasswordPolicyValidator.class),
            transactionManager,
            mock(PasswordEncoder.class),
            mock(AuthenticationManager.class),
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
