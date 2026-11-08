package com.eduhub.edu_hub.identity.infrastructure.security;

import com.eduhub.edu_hub.identity.domain.enums.RoleName;
import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.User;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Bản chụp dữ liệu xác thực; không giữ entity hoặc quan hệ JPA trong principal. */
@Getter
public final class CustomUserDetail implements UserDetails, CredentialsContainer {
    private final Long userId;
    private final String username;
    private String password;
    private final String role;
    private final UserStatus status;
    private final boolean firstLogin;
    private final boolean deleted;
    private final LocalDateTime lockedUntil;

    public CustomUserDetail(User user) {
        userId = user.getId();
        username = user.getUsername();
        password = user.getPasswordHash();
        role = RoleName.valueOf(user.getRole().getRoleName()).name();
        status = user.getStatus();
        firstLogin = user.isFirstLogin();
        deleted = user.isDeleted();
        lockedUntil = user.getLockedUntil();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.LOCKED
                && (lockedUntil == null || !LocalDateTime.now(ZoneOffset.UTC).isBefore(lockedUntil));
    }

    @Override
    public boolean isEnabled() {
        return !deleted && status != UserStatus.DISABLED;
    }

    @Override
    public void eraseCredentials() {
        password = null;
    }
}
