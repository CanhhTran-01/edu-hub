package com.eduhub.edu_hub.identity.application.facade.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.identity.api.dto.response.UserAccountEmailResponse;
import com.eduhub.edu_hub.identity.application.exception.IdentityAuthenticationException;
import com.eduhub.edu_hub.identity.application.service.CurrentUserAuthenticationService;
import com.eduhub.edu_hub.identity.application.service.TokenService;
import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.*;
import com.eduhub.edu_hub.identity.domain.repository.*;
import com.eduhub.edu_hub.identity.infrastructure.config.PasswordSettings;
import com.eduhub.edu_hub.notification.application.facade.NotificationFacade;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class IdentityProfileFacadeTest {
    UserRepository users = mock(UserRepository.class);
    RefreshTokenRepository sessions = mock(RefreshTokenRepository.class);
    TokenService tokens = new TokenService(mock(JwtEncoder.class));
    IdentityFacadeImpl facade = new IdentityFacadeImpl(
            users,
            mock(RoleRepository.class),
            new CurrentUserAuthenticationService(users, sessions, tokens),
            mock(NotificationFacade.ImmediateEmail.class),
            mock(PasswordEncoder.class),
            new PasswordSettings(8, "12345"));
    User user;
    RefreshToken session;
    Jwt authenticatedJwt = principal(7L);

    @BeforeEach
    void setup() {
        Role role = new Role();
        role.setRoleName("STUDENT");
        user = new User();
        user.setId(7L);
        user.setUsername("CT080101");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        session = new RefreshToken();
        session.setUser(user);
        session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(1));
        when(users.findByUsernameAndDeletedFalse("CT080101")).thenReturn(Optional.of(user));
        when(sessions.findByIdAndRevokedFalse(3L)).thenReturn(Optional.of(session));
    }

    @Test
    void returnsAuthenticatedStudent() {
        assertThat(facade.requireCurrentUser(authenticatedJwt).getId()).isEqualTo(7L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LECTURER", "ACADEMIC_ADMIN", "SYSTEM_ADMIN"})
    void acceptsOtherRolesWhileAuthorizationIsDeferred(String roleName) {
        user.getRole().setRoleName(roleName);
        assertThat(facade.requireCurrentUser(authenticatedJwt).getId()).isEqualTo(7L);
    }

    @Test
    void rejectsExpiredSession() {
        session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        assertThatThrownBy(() -> facade.requireCurrentUser(authenticatedJwt)).isInstanceOf(BaseException.class);
    }

    @Test
    void rejectsRevokedSession() {
        when(sessions.findByIdAndRevokedFalse(3L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> facade.requireCurrentUser(authenticatedJwt)).isInstanceOf(BaseException.class);
    }

    @Test
    void rejectsDisabledAccount() {
        user.setStatus(UserStatus.DISABLED);
        assertThatThrownBy(() -> facade.requireCurrentUser(authenticatedJwt)).isInstanceOf(BaseException.class);
    }

    @Test
    void rejectsTokenOfDifferentUser() {
        assertThatThrownBy(() -> facade.requireCurrentUser(principal(99L))).isInstanceOf(BaseException.class);
    }

    @Test
    void rejectsTemporarilyLockedAccount() {
        user.setLockedUntil(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5));
        assertThatThrownBy(() -> facade.requireCurrentUser(authenticatedJwt))
                .isInstanceOfSatisfying(
                        IdentityAuthenticationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    void rejectsDeletedAccount() {
        when(users.findByUsernameAndDeletedFalse("CT080101")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> facade.requireCurrentUser(authenticatedJwt))
                .isInstanceOf(IdentityAuthenticationException.class);
    }

    @Test
    void rejectsSessionOwnedByAnotherUser() {
        User otherUser = new User();
        otherUser.setId(99L);
        session.setUser(otherUser);
        assertThatThrownBy(() -> facade.requireCurrentUser(authenticatedJwt))
                .isInstanceOf(IdentityAuthenticationException.class);
    }

    @Test
    void rejectsMissingPrincipalBeforeDatabaseAccess() {
        assertThatThrownBy(() -> facade.requireCurrentUser(null))
                .isInstanceOfSatisfying(
                        IdentityAuthenticationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        verifyNoInteractions(users, sessions);
    }

    @Test
    void synchronizesChangedEmailOnTheManagedAccount() {
        user.setEmail("old@example.com");
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        facade.updateAccountEmail(7L, "new@example.com");
        assertThat(user.getEmail()).isEqualTo("new@example.com");
        verify(users).save(user);
    }

    @Test
    void clearsAccountEmailWithExplicitNull() {
        user.setEmail("old@example.com");
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        facade.updateAccountEmail(7L, null);
        assertThat(user.getEmail()).isNull();
        verify(users).save(user);
    }

    @Test
    void unchangedAccountEmailDoesNotWrite() {
        user.setEmail("same@example.com");
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        facade.updateAccountEmail(7L, "same@example.com");
        verify(users, never()).save(any());
    }

    @Test
    void emailSynchronizationFailsWhenAccountNoLongerExists() {
        assertThatThrownBy(() -> facade.updateAccountEmail(7L, "new@example.com"))
                .isInstanceOf(IdentityAuthenticationException.class);
        verify(users, never()).save(any());
    }

    @Test
    void emailUpdatesAreNormalizedAtTheIdentityBoundary() {
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        facade.updateAccountEmail(7L, "  Account@Example.COM  ");
        assertThat(user.getEmail()).isEqualTo("account@example.com");
    }

    @Test
    void accountEmailLookupUsesBatchesAndReturnsStableUserIdOrder() {
        Set<Long> userIds = LongStream.rangeClosed(1, 501).boxed().collect(Collectors.toSet());
        when(users.findAccountEmailsByUserIds(anySet())).thenAnswer(invocation -> {
            Set<Long> batchUserIds = invocation.getArgument(0);
            return batchUserIds.stream()
                    .map(userId -> new UserAccountEmailResponse(userId, "account" + userId + "@example.com"))
                    .toList();
        });

        var accountEmails = facade.findAccountEmailsByUserIds(userIds);

        assertThat(accountEmails).hasSize(501);
        assertThat(accountEmails.getFirst().userId()).isEqualTo(1L);
        assertThat(accountEmails.getLast().userId()).isEqualTo(501L);
        var batches = ArgumentCaptor.forClass(Set.class);
        verify(users, times(2)).findAccountEmailsByUserIds(batches.capture());
        assertThat(batches.getAllValues()).extracting(batch -> batch.size()).containsExactly(500, 1);
    }

    @Test
    void accountEmailLookupPreservesNullAndDoesNotCreateMissingAccounts() {
        when(users.findAccountEmailsByUserIds(Set.of(7L, 8L)))
                .thenReturn(List.of(new UserAccountEmailResponse(7L, null)));
        assertThat(facade.findAccountEmailsByUserIds(Set.of(7L, 8L)))
                .containsExactly(new UserAccountEmailResponse(7L, null));
        verify(users, never()).save(any());
    }

    @Test
    void emptyAccountEmailLookupDoesNotQuery() {
        assertThat(facade.findAccountEmailsByUserIds(Set.of())).isEmpty();
        verifyNoInteractions(users);
    }

    @Test
    void emailIdentityLookupIsNormalizedAndNeverFiltersByRole() {
        when(users.findUserIdsByEmail("shared@example.com")).thenReturn(Set.of(7L, 8L));
        assertThat(facade.findUserIdsByEmail("  Shared@Example.COM  ")).containsExactlyInAnyOrder(7L, 8L);
        assertThat(facade.findUserIdsByEmail("   ")).isEmpty();
        verify(users, times(1)).findUserIdsByEmail(anyString());
    }

    private static Jwt principal(Long userId) {
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("CT080101")
                .claim("uid", userId)
                .claim("sid", 3L)
                .build();
    }
}
