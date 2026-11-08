package com.eduhub.edu_hub.identity.application.facade.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.identity.api.dto.request.CreateLecturerAccountRequest;
import com.eduhub.edu_hub.identity.api.dto.request.CreateStudentAccountRequest;
import com.eduhub.edu_hub.identity.application.service.TokenService;
import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.Role;
import com.eduhub.edu_hub.identity.domain.model.User;
import com.eduhub.edu_hub.identity.domain.repository.RefreshTokenRepository;
import com.eduhub.edu_hub.identity.domain.repository.RoleRepository;
import com.eduhub.edu_hub.identity.domain.repository.UserRepository;
import com.eduhub.edu_hub.identity.infrastructure.config.PasswordSettings;
import com.eduhub.edu_hub.notification.application.command.SendAccountEmailCommand;
import com.eduhub.edu_hub.notification.application.command.SendAdmissionConfirmationEmailCommand;
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
    private final RoleRepository roles = mock(RoleRepository.class);
    private final NotificationFacade.ImmediateEmail immediateEmailNotifications =
            mock(NotificationFacade.ImmediateEmail.class);
    private final IdentityFacadeImpl identity = new IdentityFacadeImpl(
            users,
            roles,
            mock(RefreshTokenRepository.class),
            mock(TokenService.class),
            immediateEmailNotifications,
            new BCryptPasswordEncoder(4),
            new PasswordSettings(8, "configured-temp"));
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
        user.setPasswordHash(encoder.encode("configured-temp"));
        user.setFirstLogin(true);
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(role);
        when(users.findById(7L)).thenReturn(Optional.of(user));
    }

    @Test
    void delegatesOnlyAfterAccountChecksAndKeepsDeliveryErrorContract() {
        when(immediateEmailNotifications.sendAccountEmail(any()))
                .thenReturn(
                        EmailDeliveryResult.builder().error("EMAIL_SEND_FAILED").build());
        var result = identity.sendStudentAccountNotification(7L);
        assertThat(result.emailSent()).isFalse();
        assertThat(result.emailError()).isEqualTo("EMAIL_SEND_FAILED");
        verify(immediateEmailNotifications)
                .sendAccountEmail(new SendAccountEmailCommand(
                        "student@example.com", "CNTT080101", "configured-temp", EmailTemplateType.ACCOUNT_CREATED));
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
        assertThat(identity.sendStudentAccountNotification(7L).emailError()).isEqualTo(error);
        verifyNoInteractions(immediateEmailNotifications);
    }

    @Test
    void reEnrollmentConfirmationKeepsLockedAccountAndDoesNotSendInitialPassword() {
        user.setStatus(UserStatus.LOCKED);
        user.setFirstLogin(false);
        when(immediateEmailNotifications.sendAdmissionConfirmationEmail(any()))
                .thenReturn(EmailDeliveryResult.builder().sent(true).build());
        assertThat(identity.sendStudentAdmissionNotification(7L, "new@example.com")
                        .emailSent())
                .isTrue();
        verify(immediateEmailNotifications)
                .sendAdmissionConfirmationEmail(
                        new SendAdmissionConfirmationEmailCommand("new@example.com", "CNTT080101"));
        verifyNoMoreInteractions(immediateEmailNotifications);
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(user.isFirstLogin()).isFalse();
        assertThat(encoder.matches("configured-temp", user.getPasswordHash())).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "LECTURER"})
    void createsAccountsWithTheSameConfiguredTemporaryPasswordAndMandatoryFirstLogin(String roleName) {
        Role role = new Role();
        role.setRoleName(roleName);
        when(roles.findByRoleNameAndDeletedFalse(roleName)).thenReturn(Optional.of(role));
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User created = invocation.getArgument(0);
            created.setId(8L);
            return created;
        });
        if (roleName.equals("STUDENT"))
            identity.createStudentAccountWithoutNotification(
                    new CreateStudentAccountRequest("new-student", "student@example.com"));
        else
            identity.createLecturerAccountWithoutNotification(
                    new CreateLecturerAccountRequest("new-lecturer", "lecturer@example.com"));
        var createdAccount = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).save(createdAccount.capture());
        assertThat(createdAccount.getValue().isFirstLogin()).isTrue();
        assertThat(createdAccount.getValue().getPreviousPasswordHash()).isNull();
        assertThat(encoder.matches("configured-temp", createdAccount.getValue().getPasswordHash()))
                .isTrue();
        verifyNoInteractions(immediateEmailNotifications);
    }
}
