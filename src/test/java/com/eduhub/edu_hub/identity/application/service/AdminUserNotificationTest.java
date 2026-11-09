package com.eduhub.edu_hub.identity.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.RefreshToken;
import com.eduhub.edu_hub.identity.domain.model.User;
import com.eduhub.edu_hub.identity.domain.repository.RefreshTokenRepository;
import com.eduhub.edu_hub.identity.domain.repository.RoleRepository;
import com.eduhub.edu_hub.identity.domain.repository.UserRepository;
import com.eduhub.edu_hub.notification.api.dto.request.SendAccountEmailRequest;
import com.eduhub.edu_hub.notification.api.dto.response.EmailDeliveryResponse;
import com.eduhub.edu_hub.notification.application.facade.NotificationFacade;
import com.eduhub.edu_hub.notification.domain.enums.EmailTemplateType;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

class AdminUserNotificationTest {
    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenRepository sessions = mock(RefreshTokenRepository.class);
    private final TokenService tokens = mock(TokenService.class);
    private final CurrentUserAuthenticationService currentUserAuthenticationService =
            new CurrentUserAuthenticationService(users, sessions, tokens);
    private final Jwt authenticatedJwt = Jwt.withTokenValue("unit-token")
            .header("alg", "HS256")
            .subject("admin")
            .claim("uid", 1L)
            .claim("sid", 3L)
            .build();
    private final NotificationFacade.ImmediateEmail immediateEmailNotifications =
            mock(NotificationFacade.ImmediateEmail.class);
    private final AdminUserService service = new AdminUserService(
            users,
            sessions,
            mock(RoleRepository.class),
            currentUserAuthenticationService,
            tokens,
            immediateEmailNotifications,
            new BCryptPasswordEncoder(4));
    private User target;

    @BeforeEach
    void accounts() {
        var actor = new User();
        actor.setId(1L);
        actor.setStatus(UserStatus.ACTIVE);
        var session = new RefreshToken();
        session.setUser(actor);
        session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(1));
        target = new User();
        target.setId(2L);
        target.setStatus(UserStatus.ACTIVE);
        target.setUsername("CNTT080101");
        target.setEmail("student@example.com");
        when(tokens.requireAuthenticatedClaims(authenticatedJwt))
                .thenReturn(new TokenService.TokenClaims(1L, 3L, "admin"));
        when(users.findByUsernameAndDeletedFalse("admin")).thenReturn(Optional.of(actor));
        when(sessions.findByIdAndRevokedFalse(3L)).thenReturn(Optional.of(session));
        target.setPasswordHash("current-official-hash");
        target.setPreviousPasswordHash("older-official-hash");
        when(users.findByIdForUpdate(2L)).thenReturn(Optional.of(target));
        when(tokens.generateTemporaryPassword()).thenReturn("new-temporary-password");
    }

    @Test
    void resetExplicitlySelectsTheResetTemplateAndIncludesTargetUsername() {
        when(immediateEmailNotifications.sendAccountEmail(any()))
                .thenReturn(EmailDeliveryResponse.builder().sent(true).build());
        service.resetUserPassword(authenticatedJwt, 2L);
        verify(immediateEmailNotifications)
                .sendAccountEmail(SendAccountEmailRequest.builder()
                        .recipient("student@example.com")
                        .username("CNTT080101")
                        .temporaryPassword("new-temporary-password")
                        .templateType(EmailTemplateType.PASSWORD_RESET)
                        .build());
        assertThat(target.isFirstLogin()).isTrue();
        assertThat(target.getPreviousPasswordHash()).isEqualTo("current-official-hash");
    }

    @Test
    void repeatedResetDoesNotReplaceOfficialHistoryWithTemporaryPassword() {
        target.setFirstLogin(true);
        when(immediateEmailNotifications.sendAccountEmail(any()))
                .thenReturn(EmailDeliveryResponse.builder().sent(true).build());
        service.resetUserPassword(authenticatedJwt, 2L);
        assertThat(target.getPreviousPasswordHash()).isEqualTo("older-official-hash");
    }

    @ParameterizedTest
    @ValueSource(strings = {"EMAIL_SEND_FAILED", "EMAIL_TEMPLATE_RENDER_FAILED"})
    void failedDeliveryStillThrowsTheErrorUsedToRollBackPasswordReset(String error) {
        when(immediateEmailNotifications.sendAccountEmail(any()))
                .thenReturn(EmailDeliveryResponse.builder().error(error).build());
        assertThatThrownBy(() -> service.resetUserPassword(authenticatedJwt, 2L))
                .isInstanceOfSatisfying(BaseException.class, failure -> assertThat(failure.getErrorCode())
                        .isEqualTo(ErrorCode.PASSWORD_EMAIL_FAILED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "LOCKED"})
    void resetClearsTemporaryLockAndRevokesAllSessionsWhilePreservingManualStatus(String status) {
        target.setStatus(UserStatus.valueOf(status));
        target.setLockedUntil(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10));
        target.setFailedLoginCount(5);
        var firstSession = new RefreshToken();
        var secondSession = new RefreshToken();
        when(sessions.findByUser_IdAndRevokedFalse(2L)).thenReturn(List.of(firstSession, secondSession));
        when(immediateEmailNotifications.sendAccountEmail(any()))
                .thenReturn(EmailDeliveryResponse.builder().sent(true).build());

        service.resetUserPassword(authenticatedJwt, 2L);

        assertThat(target.getStatus()).isEqualTo(UserStatus.valueOf(status));
        assertThat(target.getLockedUntil()).isNull();
        assertThat(target.getFailedLoginCount()).isZero();
        assertThat(firstSession.isRevoked()).isTrue();
        assertThat(secondSession.isRevoked()).isTrue();
        assertThat(target.getPasswordChangedAt()).isNotNull();
        assertThat(new BCryptPasswordEncoder(4).matches("new-temporary-password", target.getPasswordHash()))
                .isTrue();
        verify(tokens, never()).newRefreshToken();
    }

    @Test
    void missingEmailDoesNotChangePasswordOrSendNotification() {
        target.setEmail(null);
        assertThatThrownBy(() -> service.resetUserPassword(authenticatedJwt, 2L))
                .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.ACCOUNT_EMAIL_MISSING));
        assertThat(target.getPasswordHash()).isEqualTo("current-official-hash");
        assertThat(target.isFirstLogin()).isFalse();
        verifyNoInteractions(immediateEmailNotifications);
        verify(tokens, never()).generateTemporaryPassword();
    }

    @Test
    void selfResetRemainsAllowedAndRevokesCurrentSession() {
        target.setUsername("admin");
        when(users.findByUsernameAndDeletedFalse("admin")).thenReturn(Optional.of(target));
        when(tokens.requireAuthenticatedClaims(authenticatedJwt))
                .thenReturn(new TokenService.TokenClaims(2L, 3L, "admin"));
        var currentSession = new RefreshToken();
        currentSession.setUser(target);
        currentSession.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(1));
        when(sessions.findByIdAndRevokedFalse(3L)).thenReturn(Optional.of(currentSession));
        when(sessions.findByUser_IdAndRevokedFalse(2L)).thenReturn(List.of(currentSession));
        when(immediateEmailNotifications.sendAccountEmail(any()))
                .thenReturn(EmailDeliveryResponse.builder().sent(true).build());

        service.resetUserPassword(authenticatedJwt, 2L);

        assertThat(currentSession.isRevoked()).isTrue();
        assertThat(target.isFirstLogin()).isTrue();
        var queryOrder = inOrder(users);
        queryOrder.verify(users).findByIdForUpdate(2L);
        queryOrder.verify(users).findByUsernameAndDeletedFalse("admin");
    }
}
