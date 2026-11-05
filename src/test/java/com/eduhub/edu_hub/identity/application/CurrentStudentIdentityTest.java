package com.eduhub.edu_hub.identity.application;

import com.eduhub.edu_hub.identity.infrastructure.mail.TemporaryPasswordMailService;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.identity.application.facade.impl.IdentityFacadeImpl;
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
}
