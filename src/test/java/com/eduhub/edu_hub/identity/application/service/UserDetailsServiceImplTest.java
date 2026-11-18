package com.eduhub.edu_hub.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.Role;
import com.eduhub.edu_hub.identity.domain.model.User;
import com.eduhub.edu_hub.identity.domain.repository.UserRepository;
import com.eduhub.edu_hub.shared.config.TimeConfig;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UserDetailsServiceImplTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-18T17:30:00Z"),
            new TimeConfig().applicationClock().getZone());
    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserDetailsServiceImpl service = new UserDetailsServiceImpl(userRepository, CLOCK);

    @ParameterizedTest
    @CsvSource({"-1, true", "0, true", "1, false"})
    void checksTemporaryLockAgainstSharedClockInVietnamTime(int secondsUntilUnlock, boolean unlocked) {
        Role role = new Role();
        role.setRoleName("STUDENT");
        User user = new User();
        user.setId(7L);
        user.setUsername("student");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setLockedUntil(LocalDateTime.parse("2026-11-19T00:30:00").plusSeconds(secondsUntilUnlock));
        when(userRepository.findByUsernameForUpdate("student")).thenReturn(Optional.of(user));

        assertThat(service.loadUserByUsername("student").isAccountNonLocked()).isEqualTo(unlocked);
    }
}
