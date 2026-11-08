package com.eduhub.edu_hub.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eduhub.edu_hub.identity.application.exception.IdentityAuthenticationException;
import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.Role;
import com.eduhub.edu_hub.identity.domain.model.User;
import com.eduhub.edu_hub.identity.domain.repository.RefreshTokenRepository;
import com.eduhub.edu_hub.identity.domain.repository.UserRepository;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {
    @Mock
    UserRepository users;

    @Mock
    RefreshTokenRepository sessions;

    @Mock
    TokenService tokens;

    @InjectMocks
    AdminUserService service;

    @Test
    void rejectsSelfLockOfOnlyActiveSystemAdmin() {
        Role role = new Role();
        role.setRoleName("SYSTEM_ADMIN");
        User admin = new User();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setRole(role);
        admin.setStatus(UserStatus.ACTIVE);
        Jwt authenticatedJwt = principal("admin", 2L);
        when(tokens.requireAuthenticatedClaims(authenticatedJwt))
                .thenReturn(new TokenService.TokenClaims(1L, 2L, "admin"));
        when(users.findByUsernameAndDeletedFalse("admin")).thenReturn(Optional.of(admin));
        when(sessions.findByIdAndRevokedFalse(2L)).thenReturn(Optional.of(session(admin)));
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        when(users.countByDeletedFalseAndStatusAndRole_RoleName(UserStatus.ACTIVE, "SYSTEM_ADMIN"))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.lock(authenticatedJwt, 1L, "test")).isInstanceOf(BaseException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "LECTURER", "ACADEMIC_ADMIN"})
    void authenticatedUsersCanLockAnotherAccountWithoutSystemAdminRole(String roleName) {
        Role role = new Role();
        role.setRoleName(roleName);
        User actor = new User();
        actor.setId(1L);
        actor.setUsername("actor");
        actor.setRole(role);
        actor.setStatus(UserStatus.ACTIVE);
        User target = new User();
        target.setId(2L);
        target.setStatus(UserStatus.ACTIVE);
        var targetSession = session(target);
        Jwt authenticatedJwt = principal("actor", 3L);
        when(tokens.requireAuthenticatedClaims(authenticatedJwt))
                .thenReturn(new TokenService.TokenClaims(1L, 3L, "actor"));
        when(users.findByUsernameAndDeletedFalse("actor")).thenReturn(Optional.of(actor));
        when(sessions.findByIdAndRevokedFalse(3L)).thenReturn(Optional.of(session(actor)));
        when(users.findById(2L)).thenReturn(Optional.of(target));
        when(sessions.findByUser_IdAndRevokedFalse(2L)).thenReturn(List.of(targetSession));

        service.lock(authenticatedJwt, 2L, "test");

        assertThat(target.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(targetSession.isRevoked()).isTrue();
    }

    @Test
    void missingPrincipalStillCannotManageAccounts() {
        when(tokens.requireAuthenticatedClaims(null))
                .thenThrow(new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED));
        assertThatThrownBy(() -> service.lock(null, 2L, "test"))
                .isInstanceOfSatisfying(BaseException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHENTICATED));
        verifyNoInteractions(users, sessions);
    }

    private Jwt principal(String username, Long sessionId) {
        return Jwt.withTokenValue("unit-token")
                .header("alg", "HS256")
                .subject(username)
                .claim("uid", 1L)
                .claim("sid", sessionId)
                .build();
    }

    private com.eduhub.edu_hub.identity.domain.model.RefreshToken session(User user) {
        var session = new com.eduhub.edu_hub.identity.domain.model.RefreshToken();
        session.setUser(user);
        return session;
    }
}
