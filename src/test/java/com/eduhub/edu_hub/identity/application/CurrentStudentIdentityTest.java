package com.eduhub.edu_hub.identity.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.eduhub.edu_hub.identity.domain.model.*;
import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.repository.*;
import com.eduhub.edu_hub.shared.exception.BaseException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CurrentStudentIdentityTest {
    UserRepository users = mock(UserRepository.class);
    RefreshTokenRepository sessions = mock(RefreshTokenRepository.class);
    TokenService tokens = mock(TokenService.class);
    IdentityFacadeImpl facade = new IdentityFacadeImpl(users, mock(RoleRepository.class), sessions,
            tokens, mock(TemporaryPasswordMailService.class));
    User user; RefreshToken session;

    @BeforeEach void setup() {
        Role role = new Role(); role.setRoleName("STUDENT");
        user = new User(); user.setId(7L); user.setUsername("CT080101"); user.setRole(role); user.setStatus(UserStatus.ACTIVE);
        session = new RefreshToken(); session.setUser(user);
        session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(1));
        when(tokens.validateAccessToken("token")).thenReturn(new TokenService.TokenClaims(7L, 3L, "CT080101"));
        when(users.findByUsernameAndDeletedFalse("CT080101")).thenReturn(Optional.of(user));
        when(sessions.findByIdAndRevokedFalse(3L)).thenReturn(Optional.of(session));
    }

    @Test void returnsAuthenticatedStudent() {
        assertThat(facade.requireCurrentStudent("token").getId()).isEqualTo(7L);
    }
    @Test void rejectsNonStudentRole() {
        user.getRole().setRoleName("ACADEMIC_ADMIN");
        assertThatThrownBy(() -> facade.requireCurrentStudent("token")).isInstanceOf(BaseException.class);
    }
    @Test void rejectsExpiredSession() {
        session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        assertThatThrownBy(() -> facade.requireCurrentStudent("token")).isInstanceOf(BaseException.class);
    }
    @Test void rejectsRevokedSession() {
        when(sessions.findByIdAndRevokedFalse(3L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> facade.requireCurrentStudent("token")).isInstanceOf(BaseException.class);
    }
    @Test void rejectsDisabledAccount() {
        user.setStatus(UserStatus.DISABLED);
        assertThatThrownBy(() -> facade.requireCurrentStudent("token")).isInstanceOf(BaseException.class);
    }
    @Test void rejectsTokenOfDifferentUser() {
        when(tokens.validateAccessToken("token")).thenReturn(new TokenService.TokenClaims(99L, 3L, "CT080101"));
        assertThatThrownBy(() -> facade.requireCurrentStudent("token")).isInstanceOf(BaseException.class);
    }

    @Test void disablingAccountRevokesEveryActiveSession() {
        RefreshToken second = new RefreshToken();
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(sessions.findByUser_IdAndRevokedFalse(7L)).thenReturn(java.util.List.of(session, second));

        facade.disableUser(7L);

        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
        assertThat(session.isRevoked()).isTrue();
        assertThat(second.isRevoked()).isTrue();
    }

    @Test void enablingAccountClearsTemporaryLockState() {
        user.setStatus(UserStatus.DISABLED);
        user.setFailedLoginCount(5);
        user.setLockedUntil(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10));
        when(users.findById(7L)).thenReturn(Optional.of(user));

        facade.enableUser(7L);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }
}
