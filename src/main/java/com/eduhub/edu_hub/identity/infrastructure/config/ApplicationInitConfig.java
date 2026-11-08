package com.eduhub.edu_hub.identity.infrastructure.config;

import com.eduhub.edu_hub.identity.application.exception.IdentityConfigurationException;
import com.eduhub.edu_hub.identity.domain.enums.RoleName;
import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.Role;
import com.eduhub.edu_hub.identity.domain.model.User;
import com.eduhub.edu_hub.identity.domain.repository.RefreshTokenRepository;
import com.eduhub.edu_hub.identity.domain.repository.RoleRepository;
import com.eduhub.edu_hub.identity.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@RequiredArgsConstructor
public class ApplicationInitConfig {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PlatformTransactionManager transactionManager;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;

    @Bean
    public ApplicationRunner initializeSystemAdmin(
            @Value("${app.init.system-admin.username}") String username,
            @Value("${app.init.system-admin.email}") String email,
            @Value("${app.init.system-admin.password}") String password) {
        return args -> {
            if (username.isBlank() || password.isBlank()) {
                throw new IdentityConfigurationException(
                        "Username và mật khẩu khởi tạo quản trị hệ thống không được trống");
            }

            try {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {

                    // tạo role system admin để gán cho tài khoản init
                    Role role = roleRepository
                            .findByRoleNameAndDeletedFalse(RoleName.SYSTEM_ADMIN.name())
                            .orElseGet(() -> {
                                Role systemAdminRole = new Role();
                                systemAdminRole.setRoleName(RoleName.SYSTEM_ADMIN.name());
                                return roleRepository.save(systemAdminRole);
                            });

                    // Tạo lại tài khoản đã xóa mềm/vô hiệu hóa, tài khoản ACTIVE hoặc LOCKED vẫn được giữ nguyên.
                    User existingAccount = userRepository
                            .findByUsernameIncludingDeletedForUpdate(username)
                            .orElse(null);
                    if (existingAccount != null) {
                        if (!existingAccount.isDeleted() && existingAccount.getStatus() != UserStatus.DISABLED) return;
                        refreshTokenRepository.deleteAllByUser_Id(existingAccount.getId());
                        userRepository.delete(existingAccount);
                        // Xóa trước khi INSERT để giải phóng username; FK vẫn chặn xóa và rollback cả transaction.
                        userRepository.flush();
                    }

                    // Tạo tài khoản với role system admin
                    User user = new User();
                    user.setUsername(username);
                    user.setEmail(email.isBlank() ? null : email);
                    user.setPasswordHash(passwordEncoder.encode(password));
                    user.setRole(role);
                    user.setStatus(UserStatus.ACTIVE);
                    user.setFirstLogin(false);
                    userRepository.save(user);
                });
            } catch (DataIntegrityViolationException exception) {
                throw new IdentityConfigurationException(
                        "Không thể tạo lại tài khoản quản trị: tài khoản còn dữ liệu liên kết hoặc username đã được sử dụng. Transaction đã được rollback.",
                        exception);
            }
        };
    }
}
