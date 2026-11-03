package com.eduhub.edu_hub.identity.application;

import com.eduhub.edu_hub.identity.api.dto.UserInfoResponse;
import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.Role;
import com.eduhub.edu_hub.identity.domain.model.User;
import com.eduhub.edu_hub.identity.domain.repository.RoleRepository;
import com.eduhub.edu_hub.identity.domain.repository.UserRepository;
import com.eduhub.edu_hub.shared.enums.RoleName;
import com.eduhub.edu_hub.shared.exception.BaseException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdentityFacadeImpl implements IdentityFacade {
    private final UserRepository users;
    private final RoleRepository roles;
    private final TemporaryPasswordMailService mailService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    @Transactional
    public Long createUser(String username, String rawPassword, String roleName, boolean isFirstLogin) {
        Role role = roles.findByRoleNameAndDeletedFalse(roleName)
                .orElseThrow(() -> new BaseException(ErrorCode.VALIDATION_ERROR));
        return save(username, null, rawPassword, role, isFirstLogin).getId();
    }

    @Override
    @Transactional
    public CreateStudentAccountResult createStudentAccount(CreateStudentAccountCommand command) {
        if (users.existsByUsername(command.username())) {
            throw new BaseException(ErrorCode.DUPLICATE_STUDENT_CODE);
        }
        Role role = roles.findByRoleNameAndDeletedFalse(RoleName.STUDENT.name())
                .orElseThrow(() -> new BaseException(ErrorCode.VALIDATION_ERROR));
        User user = save(command.username(), command.email(), command.initialPassword(), role,
                command.requirePasswordChange());
        try {
            mailService.send(command.email(), command.initialPassword());
            return new CreateStudentAccountResult(user.getId(), true, null);
        } catch (MailException exception) {
            log.error("Initial account email failed for user {}", user.getId());
            return new CreateStudentAccountResult(user.getId(), false, "EMAIL_SEND_FAILED");
        }
    }

    private User save(String username, String email, String rawPassword, Role role, boolean firstLogin) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(rawPassword));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setFirstLogin(firstLogin);
        return users.save(user);
    }

    @Override public void lockUser(Long userId, String reason) { throw new UnsupportedOperationException(); }
    @Override public void unlockUser(Long userId) { throw new UnsupportedOperationException(); }
    @Override public void disableUser(Long userId) { user(userId).setStatus(UserStatus.DISABLED); }
    @Override public void enableUser(Long userId) { user(userId).setStatus(UserStatus.ACTIVE); }
    @Override public String resetPassword(Long userId) { throw new UnsupportedOperationException(); }
    @Override public Optional<UserInfoResponse> findById(Long userId) { return users.findById(userId).map(this::response); }
    @Override public Optional<UserInfoResponse> findByUsername(String username) { return users.findByUsernameAndDeletedFalse(username).map(this::response); }
    @Override public boolean existsByUsername(String username) { return users.existsByUsername(username); }

    private User user(Long id) { return users.findById(id).orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND)); }
    private UserInfoResponse response(User user) {
        return UserInfoResponse.builder().id(user.getId()).username(user.getUsername()).email(user.getEmail())
                .role(user.getRole().getRoleName()).status(user.getStatus().name())
                .isFirstLogin(user.isFirstLogin()).build();
    }
}
