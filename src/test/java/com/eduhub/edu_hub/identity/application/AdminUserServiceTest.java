package com.eduhub.edu_hub.identity.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.Role;
import com.eduhub.edu_hub.identity.domain.model.User;
import com.eduhub.edu_hub.identity.domain.repository.RefreshTokenRepository;
import com.eduhub.edu_hub.identity.domain.repository.UserRepository;
import com.eduhub.edu_hub.shared.exception.BaseException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        when(tokens.validateAccessToken("token")).thenReturn(new TokenService.TokenClaims(1L, 2L, "admin"));
        when(users.findByUsernameAndDeletedFalse("admin")).thenReturn(Optional.of(admin));
        when(sessions.findByIdAndRevokedFalse(2L)).thenReturn(Optional.of(session(admin)));
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        when(users.countByDeletedFalseAndStatusAndRole_RoleName(UserStatus.ACTIVE, "SYSTEM_ADMIN"))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.lock("token", 1L, "test")).isInstanceOf(BaseException.class);
    }

    private com.eduhub.edu_hub.identity.domain.model.RefreshToken session(User user) {
        var session = new com.eduhub.edu_hub.identity.domain.model.RefreshToken();
        session.setUser(user);
        return session;
    }
}
