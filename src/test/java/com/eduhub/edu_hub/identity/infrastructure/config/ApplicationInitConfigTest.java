package com.eduhub.edu_hub.identity.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.Role;
import com.eduhub.edu_hub.identity.domain.model.User;
import com.eduhub.edu_hub.identity.domain.repository.RefreshTokenRepository;
import com.eduhub.edu_hub.identity.domain.repository.RoleRepository;
import com.eduhub.edu_hub.identity.domain.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ApplicationInitConfigTest {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final ApplicationInitConfig configuration = new ApplicationInitConfig(
            userRepository, roleRepository, transactionManager, passwordEncoder, refreshTokenRepository);

    @Test
    void createsMissingRoleAndAccountWithEncodedPassword() throws Exception {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(roleRepository.findByRoleNameAndDeletedFalse("SYSTEM_ADMIN")).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));
        configuration.initializeSystemAdmin("sysadmin", "", "sysadmin@123").run(null);

        ArgumentCaptor<User> createdAccount = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(createdAccount.capture());
        User account = createdAccount.getValue();
        assertThat(account.getUsername()).isEqualTo("sysadmin");
        assertThat(account.getRole().getRoleName()).isEqualTo("SYSTEM_ADMIN");
        assertThat(account.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(account.isFirstLogin()).isFalse();
        assertThat(account.getEmail()).isNull();
        assertThat(account.getPasswordHash()).isNotEqualTo("sysadmin@123");
        assertThat(passwordEncoder.matches("sysadmin@123", account.getPasswordHash()))
                .isTrue();
        verify(transactionManager).commit(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "LOCKED"})
    void existingAvailableOrLockedAccountIsNotRecreated(String status) throws Exception {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        Role role = new Role();
        role.setRoleName("SYSTEM_ADMIN");
        when(roleRepository.findByRoleNameAndDeletedFalse("SYSTEM_ADMIN")).thenReturn(Optional.of(role));
        User existingAccount = new User();
        existingAccount.setStatus(UserStatus.valueOf(status));
        when(userRepository.findByUsernameIncludingDeletedForUpdate("sysadmin"))
                .thenReturn(Optional.of(existingAccount));
        configuration.initializeSystemAdmin("sysadmin", "", "sysadmin@123").run(null);
        verify(userRepository, never()).save(any());
        verify(roleRepository, never()).save(any());
        verifyNoInteractions(refreshTokenRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deletedOrDisabledAccountIsRemovedBeforeCreatingReplacement(boolean deleted) throws Exception {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        Role role = new Role();
        role.setRoleName("SYSTEM_ADMIN");
        when(roleRepository.findByRoleNameAndDeletedFalse("SYSTEM_ADMIN")).thenReturn(Optional.of(role));
        User existingAccount = new User();
        existingAccount.setId(7L);
        existingAccount.setDeleted(deleted);
        existingAccount.setStatus(deleted ? UserStatus.ACTIVE : UserStatus.DISABLED);
        when(userRepository.findByUsernameIncludingDeletedForUpdate("sysadmin"))
                .thenReturn(Optional.of(existingAccount));
        configuration.initializeSystemAdmin("sysadmin", "", "sysadmin@123").run(null);
        var deletionOrder = inOrder(refreshTokenRepository, userRepository);
        deletionOrder.verify(refreshTokenRepository).deleteAllByUser_Id(7L);
        deletionOrder.verify(userRepository).delete(existingAccount);
        deletionOrder.verify(userRepository).flush();
        deletionOrder.verify(userRepository).save(any(User.class));
    }
}
