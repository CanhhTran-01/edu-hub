package com.eduhub.edu_hub.identity.application;

import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.Role;
import com.eduhub.edu_hub.identity.domain.model.User;
import com.eduhub.edu_hub.identity.domain.repository.RoleRepository;
import com.eduhub.edu_hub.identity.domain.repository.UserRepository;
import com.eduhub.edu_hub.shared.enums.RoleName;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@Profile("dev")
@RequiredArgsConstructor
public class DevAdminBootstrap {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PlatformTransactionManager transactionManager;

    @Bean
    ApplicationRunner createDevAdmin(
            @Value("${DEV_ADMIN_USERNAME:}") String username,
            @Value("${DEV_ADMIN_EMAIL:}") String email,
            @Value("${DEV_ADMIN_PASSWORD:}") String password) {
        return args -> {
            if (username.isBlank() || password.isBlank()) return;
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                if (userRepository.existsByUsername(username)) return;
                Role role = roleRepository
                        .findByRoleNameAndDeletedFalse(RoleName.SYSTEM_ADMIN.name())
                        .orElseThrow(() -> new IllegalStateException("SYSTEM_ADMIN role is missing"));
                User user = new User();
                user.setUsername(username);
                user.setEmail(email.isBlank() ? null : email);
                user.setPasswordHash(new BCryptPasswordEncoder().encode(password));
                user.setRole(role);
                user.setStatus(UserStatus.ACTIVE);
                user.setFirstLogin(false);
                userRepository.save(user);
            });
        };
    }
}
