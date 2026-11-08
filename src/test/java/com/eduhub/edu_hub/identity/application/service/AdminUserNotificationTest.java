package com.eduhub.edu_hub.identity.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.RefreshToken;
import com.eduhub.edu_hub.identity.domain.model.User;
import com.eduhub.edu_hub.identity.domain.repository.RefreshTokenRepository;
import com.eduhub.edu_hub.identity.domain.repository.UserRepository;
import com.eduhub.edu_hub.notification.application.command.SendAccountEmailCommand;
import com.eduhub.edu_hub.notification.application.facade.NotificationFacade;
import com.eduhub.edu_hub.notification.application.result.EmailDeliveryResult;
import com.eduhub.edu_hub.notification.domain.enums.EmailTemplateType;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
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
    private final Jwt authenticatedJwt = Jwt.withTokenValue("unit-token")
            .header("alg", "HS256")
            .subject("admin")
            .claim("uid", 1L)
            .claim("sid", 3L)
            .build();
    private final NotificationFacade.ImmediateEmail immediateEmailNotifications =
            mock(NotificationFacade.ImmediateEmail.class);
    private final AdminUserService service =
            new AdminUserService(users, sessions, tokens, immediateEmailNotifications, new BCryptPasswordEncoder(4));
    private User target;

    @BeforeEach
    void accounts() {
        var actor = new User();
        actor.setId(1L);
        actor.setStatus(UserStatus.ACTIVE);
        var session = new RefreshToken();
        session.setUser(actor);
        target = new User();
        target.setId(2L);
        target.setUsername("CNTT080101");
        target.setEmail("student@example.com");
        when(tokens.requireAuthenticatedClaims(authenticatedJwt))
                .thenReturn(new TokenService.TokenClaims(1L, 3L, "admin"));
        when(users.findByUsernameAndDeletedFalse("admin")).thenReturn(Optional.of(actor));
        when(sessions.findByIdAndRevokedFalse(3L)).thenReturn(Optional.of(session));
        target.setPasswordHash("current-official-hash");
        target.setPreviousPasswordHash("older-official-hash");
        when(users.findByIdForUpdate(2L)).thenReturn(Optional.of(target));
        when(tokens.newRefreshToken()).thenReturn("new-temporary-password");
    }

    @Test
    void resetExplicitlySelectsTheResetTemplateAndIncludesTargetUsername() {
        when(immediateEmailNotifications.sendAccountEmail(any()))
                .thenReturn(EmailDeliveryResult.builder().sent(true).build());
        service.resetPassword(authenticatedJwt, 2L);
        verify(immediateEmailNotifications)
                .sendAccountEmail(SendAccountEmailCommand.builder()
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
                .thenReturn(EmailDeliveryResult.builder().sent(true).build());
        service.resetPassword(authenticatedJwt, 2L);
        assertThat(target.getPreviousPasswordHash()).isEqualTo("older-official-hash");
    }

    @ParameterizedTest
    @ValueSource(strings = {"EMAIL_SEND_FAILED", "EMAIL_TEMPLATE_RENDER_FAILED"})
    void failedDeliveryStillThrowsTheErrorUsedToRollBackPasswordReset(String error) {
        when(immediateEmailNotifications.sendAccountEmail(any()))
                .thenReturn(EmailDeliveryResult.builder().error(error).build());
        assertThatThrownBy(() -> service.resetPassword(authenticatedJwt, 2L))
                .isInstanceOfSatisfying(BaseException.class, failure -> assertThat(failure.getErrorCode())
                        .isEqualTo(ErrorCode.PASSWORD_EMAIL_FAILED));
    }
}
