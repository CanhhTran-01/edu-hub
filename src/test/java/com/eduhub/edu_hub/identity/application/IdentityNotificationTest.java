package com.eduhub.edu_hub.identity.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.identity.application.facade.impl.IdentityFacadeImpl;
import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.Role;
import com.eduhub.edu_hub.identity.domain.model.User;
import com.eduhub.edu_hub.identity.domain.repository.RefreshTokenRepository;
import com.eduhub.edu_hub.identity.domain.repository.RoleRepository;
import com.eduhub.edu_hub.identity.domain.repository.UserRepository;
import com.eduhub.edu_hub.notification.application.command.AccountEmailCommand;
import com.eduhub.edu_hub.notification.application.command.AdmissionEmailCommand;
import com.eduhub.edu_hub.notification.application.facade.NotificationFacade;
import com.eduhub.edu_hub.notification.application.result.EmailDeliveryResult;
import com.eduhub.edu_hub.notification.domain.enums.EmailTemplateType;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class IdentityNotificationTest {
    private final UserRepository users = mock(UserRepository.class);
    private final NotificationFacade.Mail notifications = mock(NotificationFacade.Mail.class);
    private final IdentityFacadeImpl identity = new IdentityFacadeImpl(
            users,
            mock(RoleRepository.class),
            mock(RefreshTokenRepository.class),
            mock(TokenService.class),
            notifications);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private User user;

    @BeforeEach
    void account() {
        var role = new Role();
        role.setRoleName("STUDENT");
        user = new User();
        user.setId(7L);
        user.setUsername("CNTT080101");
        user.setEmail("student@example.com");
        user.setPasswordHash(encoder.encode("12345"));
        user.setFirstLogin(true);
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(role);
        when(users.findById(7L)).thenReturn(Optional.of(user));
    }

    @Test
    void delegatesOnlyAfterAccountChecksAndKeepsDeliveryErrorContract() {
        when(notifications.sendAccount(any()))
                .thenReturn(
                        EmailDeliveryResult.builder().error("EMAIL_SEND_FAILED").build());
        var result = identity.sendStudentAccountNotification(7L, "12345");
        assertThat(result.emailSent()).isFalse();
        assertThat(result.emailError()).isEqualTo("EMAIL_SEND_FAILED");
        verify(notifications)
                .sendAccount(new AccountEmailCommand(
                        "student@example.com", "CNTT080101", "12345", EmailTemplateType.ACCOUNT_CREATED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"activated", "locked", "passwordChanged", "noEmail"})
    void accountRestrictionsAreStillCheckedBeforeCallingNotification(String state) {
        String error =
                switch (state) {
                    case "activated" -> {
                        user.setFirstLogin(false);
                        yield "ACCOUNT_ALREADY_ACTIVATED";
                    }
                    case "locked" -> {
                        user.setStatus(UserStatus.LOCKED);
                        yield "ACCOUNT_NOT_AVAILABLE";
                    }
                    case "passwordChanged" -> {
                        user.setPasswordHash(encoder.encode("changed-password"));
                        yield "INITIAL_PASSWORD_NO_LONGER_VALID";
                    }
                    case "noEmail" -> {
                        user.setEmail(null);
                        yield "ACCOUNT_EMAIL_MISSING";
                    }
                    default -> throw new IllegalArgumentException(state);
                };
        assertThat(identity.sendStudentAccountNotification(7L, "12345").emailError())
                .isEqualTo(error);
        verifyNoInteractions(notifications);
    }

    @Test
    void reEnrollmentConfirmationKeepsLockedAccountAndDoesNotSendInitialPassword() {
        user.setStatus(UserStatus.LOCKED);
        user.setFirstLogin(false);
        when(notifications.sendAdmissionConfirmation(any()))
                .thenReturn(EmailDeliveryResult.builder().sent(true).build());
        assertThat(identity.sendStudentAdmissionNotification(7L, "new@example.com")
                        .emailSent())
                .isTrue();
        verify(notifications).sendAdmissionConfirmation(new AdmissionEmailCommand("new@example.com", "CNTT080101"));
        verifyNoMoreInteractions(notifications);
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(user.isFirstLogin()).isFalse();
        assertThat(encoder.matches("12345", user.getPasswordHash())).isTrue();
    }
}
