package com.eduhub.edu_hub.identity.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.identity.api.dto.request.LockUserRequest;
import com.eduhub.edu_hub.identity.api.dto.request.SearchUserAccountsRequest;
import com.eduhub.edu_hub.identity.application.exception.IdentityAccountException;
import com.eduhub.edu_hub.identity.application.exception.IdentityAuthenticationException;
import com.eduhub.edu_hub.identity.application.exception.IdentityConfigurationException;
import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.identity.domain.model.RefreshToken;
import com.eduhub.edu_hub.identity.domain.model.Role;
import com.eduhub.edu_hub.identity.domain.model.User;
import com.eduhub.edu_hub.identity.domain.repository.RefreshTokenRepository;
import com.eduhub.edu_hub.identity.domain.repository.RoleRepository;
import com.eduhub.edu_hub.identity.domain.repository.UserRepository;
import com.eduhub.edu_hub.notification.application.facade.NotificationFacade;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class AdminUserServiceTest {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final TokenService tokenService = new TokenService(mock(JwtEncoder.class));
    private final CurrentUserAuthenticationService currentUserAuthenticationService =
            new CurrentUserAuthenticationService(userRepository, refreshTokenRepository, tokenService);
    private final NotificationFacade.ImmediateEmail immediateEmailNotifications =
            mock(NotificationFacade.ImmediateEmail.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AdminUserService adminUserService = new AdminUserService(
            userRepository,
            refreshTokenRepository,
            roleRepository,
            currentUserAuthenticationService,
            tokenService,
            immediateEmailNotifications,
            passwordEncoder);
    private final Jwt authenticatedJwt = Jwt.withTokenValue("unit-token")
            .header("alg", "HS256")
            .subject("admin")
            .claim("uid", 1L)
            .claim("sid", 3L)
            .build();
    private final LockUserRequest lockRequest = new LockUserRequest(" Kiểm tra sự cố tài khoản ");
    private User actorUser;
    private User targetUser;
    private RefreshToken actorSession;

    @BeforeEach
    void setUp() {
        Role systemAdminRole = new Role();
        systemAdminRole.setRoleName("SYSTEM_ADMIN");
        actorUser = new User();
        actorUser.setId(1L);
        actorUser.setUsername("admin");
        actorUser.setRole(systemAdminRole);
        actorUser.setStatus(UserStatus.ACTIVE);
        targetUser = new User();
        targetUser.setId(2L);
        targetUser.setUsername("student");
        targetUser.setRole(systemAdminRole);
        targetUser.setStatus(UserStatus.ACTIVE);
        targetUser.setPasswordHash("original-hash");
        actorSession = new RefreshToken();
        actorSession.setUser(actorUser);
        actorSession.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(1));
        when(userRepository.findByUsernameAndDeletedFalse("admin")).thenReturn(Optional.of(actorUser));
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(targetUser));
        when(roleRepository.findByRoleNameForUpdate("SYSTEM_ADMIN")).thenReturn(Optional.of(systemAdminRole));
        when(refreshTokenRepository.findByIdAndRevokedFalse(3L)).thenReturn(Optional.of(actorSession));
        when(userRepository.countByDeletedFalseAndStatusAndRole_RoleName(UserStatus.ACTIVE, "SYSTEM_ADMIN"))
                .thenReturn(2L);
    }

    @Test
    void rejectsSelfLockOfOnlyActiveSystemAdminAfterAcquiringCommonAndAccountLocks() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(actorUser));
        when(userRepository.countByDeletedFalseAndStatusAndRole_RoleName(UserStatus.ACTIVE, "SYSTEM_ADMIN"))
                .thenReturn(1L);

        assertThatThrownBy(() -> adminUserService.lockUserAccount(authenticatedJwt, 1L, lockRequest))
                .isInstanceOfSatisfying(
                        IdentityAccountException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThat(actorUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(actorSession.isRevoked()).isFalse();
        var queryOrder = inOrder(roleRepository, userRepository);
        queryOrder.verify(roleRepository).findByRoleNameForUpdate("SYSTEM_ADMIN");
        queryOrder.verify(userRepository).findByIdForUpdate(1L);
        queryOrder.verify(userRepository).findByUsernameAndDeletedFalse("admin");
        queryOrder
                .verify(userRepository)
                .countByDeletedFalseAndStatusAndRole_RoleName(UserStatus.ACTIVE, "SYSTEM_ADMIN");
    }

    @Test
    void permitsSelfLockWhenAnotherSystemAdminIsActiveAndRevokesOwnSession() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(actorUser));
        when(refreshTokenRepository.findByUser_IdAndRevokedFalse(1L)).thenReturn(List.of(actorSession));
        adminUserService.lockUserAccount(authenticatedJwt, 1L, lockRequest);
        assertThat(actorUser.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(actorSession.isRevoked()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "LECTURER", "ACADEMIC_ADMIN"})
    void authenticatedUsersCanLockAnotherAccountWhileRoleAuthorizationIsDeferred(String actorRoleName) {
        actorUser.getRole().setRoleName(actorRoleName);
        var targetSession = new RefreshToken();
        when(refreshTokenRepository.findByUser_IdAndRevokedFalse(2L)).thenReturn(List.of(targetSession));
        adminUserService.lockUserAccount(authenticatedJwt, 2L, lockRequest);
        assertThat(targetUser.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(targetSession.isRevoked()).isTrue();
    }

    @Test
    void selfLockOfNonAdminIsNotBlockedBySystemAdminCount() {
        actorUser.getRole().setRoleName("STUDENT");
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(actorUser));
        when(userRepository.countByDeletedFalseAndStatusAndRole_RoleName(UserStatus.ACTIVE, "SYSTEM_ADMIN"))
                .thenReturn(1L);
        adminUserService.lockUserAccount(authenticatedJwt, 1L, lockRequest);
        assertThat(actorUser.getStatus()).isEqualTo(UserStatus.LOCKED);
    }

    @Test
    void actorIsCheckedAgainAfterWaitingForTheCommonStatusLock() {
        when(roleRepository.findByRoleNameForUpdate("SYSTEM_ADMIN")).thenAnswer(invocation -> {
            actorUser.setStatus(UserStatus.LOCKED);
            return Optional.of(actorUser.getRole());
        });
        assertThatThrownBy(() -> adminUserService.lockUserAccount(authenticatedJwt, 2L, lockRequest))
                .isInstanceOf(IdentityAuthenticationException.class);
        assertThat(targetUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userRepository, never()).countByDeletedFalseAndStatusAndRole_RoleName(any(), anyString());
    }

    @Test
    void missingSystemAdminRoleFailsExplicitlyBeforeChangingAccount() {
        when(roleRepository.findByRoleNameForUpdate("SYSTEM_ADMIN")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> adminUserService.lockUserAccount(authenticatedJwt, 2L, lockRequest))
                .isInstanceOf(IdentityConfigurationException.class);
        verify(userRepository, never()).findByIdForUpdate(any());
        assertThat(targetUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "expiredSession",
                "revokedSession",
                "wrongSessionOwner",
                "temporaryLock",
                "disabledActor",
                "lockedActor",
                "deletedActor"
            })
    void invalidActorCannotChangeAccount(String invalidState) {
        switch (invalidState) {
            case "expiredSession" -> actorSession.setExpiresAt(
                    LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
            case "revokedSession" -> when(refreshTokenRepository.findByIdAndRevokedFalse(3L))
                    .thenReturn(Optional.empty());
            case "wrongSessionOwner" -> actorSession.setUser(targetUser);
            case "temporaryLock" -> actorUser.setLockedUntil(
                    LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10));
            case "disabledActor" -> actorUser.setStatus(UserStatus.DISABLED);
            case "lockedActor" -> actorUser.setStatus(UserStatus.LOCKED);
            case "deletedActor" -> when(userRepository.findByUsernameAndDeletedFalse("admin"))
                    .thenReturn(Optional.empty());
            default -> throw new IllegalArgumentException(invalidState);
        }
        assertThatThrownBy(() -> adminUserService.lockUserAccount(authenticatedJwt, 2L, lockRequest))
                .isInstanceOfSatisfying(
                        IdentityAuthenticationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        assertThat(targetUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(refreshTokenRepository, never()).findByUser_IdAndRevokedFalse(any());
        verifyNoInteractions(immediateEmailNotifications, passwordEncoder);
    }

    @ParameterizedTest
    @ValueSource(strings = {"lock", "unlock", "reset"})
    void disabledTargetRejectsEveryAdministrativeAction(String action) {
        targetUser.setStatus(UserStatus.DISABLED);
        assertThatThrownBy(() -> performAction(action))
                .isInstanceOfSatisfying(
                        IdentityAccountException.class, exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DISABLED_ACCOUNT_OPERATION_NOT_ALLOWED));
        assertThat(targetUser.getStatus()).isEqualTo(UserStatus.DISABLED);
        assertThat(targetUser.getPasswordHash()).isEqualTo("original-hash");
        verify(refreshTokenRepository, never()).findByUser_IdAndRevokedFalse(any());
        verifyNoInteractions(immediateEmailNotifications, passwordEncoder);
    }

    @ParameterizedTest
    @ValueSource(strings = {"lock", "unlock", "reset"})
    void missingTargetIsReportedConsistently(String action) {
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> performAction(action))
                .isInstanceOfSatisfying(
                        IdentityAccountException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    void deletedTargetCannotBeReactivated() {
        targetUser.setDeleted(true);
        assertThatThrownBy(() -> adminUserService.unlockUserAccount(authenticatedJwt, 2L))
                .isInstanceOfSatisfying(
                        IdentityAccountException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @ParameterizedTest
    @EnumSource(
            value = UserStatus.class,
            names = {"ACTIVE", "LOCKED"})
    void unlockClearsTemporaryLockWithoutRestoringOldSessions(UserStatus targetStatus) {
        targetUser.setStatus(targetStatus);
        targetUser.setLockedUntil(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10));
        targetUser.setFailedLoginCount(5);
        adminUserService.unlockUserAccount(authenticatedJwt, 2L);
        assertThat(targetUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(targetUser.getLockedUntil()).isNull();
        assertThat(targetUser.getFailedLoginCount()).isZero();
        var queryOrder = inOrder(roleRepository, userRepository);
        queryOrder.verify(roleRepository).findByRoleNameForUpdate("SYSTEM_ADMIN");
        queryOrder.verify(userRepository).findByIdForUpdate(2L);
        verify(refreshTokenRepository, never()).findByUser_IdAndRevokedFalse(any());
    }

    @Test
    void lockingAlreadyLockedAccountStillRevokesRemainingSessions() {
        targetUser.setStatus(UserStatus.LOCKED);
        var remainingSession = new RefreshToken();
        when(refreshTokenRepository.findByUser_IdAndRevokedFalse(2L)).thenReturn(List.of(remainingSession));
        adminUserService.lockUserAccount(authenticatedJwt, 2L, lockRequest);
        assertThat(targetUser.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(remainingSession.isRevoked()).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void invalidReasonDoesNotAcquireDatabaseLocks(String reason) {
        assertThatThrownBy(() -> adminUserService.lockUserAccount(authenticatedJwt, 2L, new LockUserRequest(reason)))
                .isInstanceOf(IdentityAccountException.class);
        verifyNoInteractions(roleRepository, userRepository, refreshTokenRepository);
    }

    @Test
    void reasonExceedingLimitIsRejectedAtServiceBoundary() {
        assertThatThrownBy(() ->
                        adminUserService.lockUserAccount(authenticatedJwt, 2L, new LockUserRequest("a".repeat(501))))
                .isInstanceOf(IdentityAccountException.class);
        verifyNoInteractions(roleRepository, userRepository, refreshTokenRepository);
    }

    @Test
    void missingPrincipalIsRejectedByAllEntrypointsBeforeAnyDatabaseAccess() {
        assertThatThrownBy(() -> adminUserService.lockUserAccount(null, 2L, lockRequest))
                .isInstanceOf(IdentityAuthenticationException.class);
        assertThatThrownBy(() -> adminUserService.unlockUserAccount(null, 2L))
                .isInstanceOf(IdentityAuthenticationException.class);
        assertThatThrownBy(() -> adminUserService.resetUserPassword(null, 2L))
                .isInstanceOf(IdentityAuthenticationException.class);
        assertThatThrownBy(() -> adminUserService.searchUserAccounts(null, new SearchUserAccountsRequest()))
                .isInstanceOf(IdentityAuthenticationException.class);
        verifyNoInteractions(roleRepository, userRepository, refreshTokenRepository);
    }

    @Test
    void searchTrimsKeywordAndUsesStableIdOrderAndExistingResponseFields() {
        var request = new SearchUserAccountsRequest();
        request.setKeyword(" student ");
        var pageable = PageRequest.of(0, 20, Sort.by("id").ascending());
        targetUser.setEmail("student@example.com");
        targetUser.setFirstLogin(true);
        when(userRepository.findByDeletedFalseAndUsernameContainingIgnoreCase("student", pageable))
                .thenReturn(new PageImpl<>(List.of(targetUser), pageable, 1));

        var response = adminUserService.searchUserAccounts(authenticatedJwt, request);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().getFirst().getUsername()).isEqualTo("student");
        assertThat(response.getContent().getFirst().getEmail()).isEqualTo("student@example.com");
        assertThat(response.getContent().getFirst().isFirstLogin()).isTrue();
        assertThat(response.getContent().getFirst().getFullName()).isNull();
        assertThat(response.getPageNumber()).isZero();
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.isLast()).isTrue();
    }

    @Test
    void searchWithNoMatchReturnsAnEmptyPage() {
        when(userRepository.findByDeletedFalseAndUsernameContainingIgnoreCase(eq(""), any()))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(1)));
        var response = adminUserService.searchUserAccounts(authenticatedJwt, new SearchUserAccountsRequest());
        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
    }

    private void performAction(String action) {
        switch (action) {
            case "lock" -> adminUserService.lockUserAccount(authenticatedJwt, 2L, lockRequest);
            case "unlock" -> adminUserService.unlockUserAccount(authenticatedJwt, 2L);
            case "reset" -> adminUserService.resetUserPassword(authenticatedJwt, 2L);
            default -> throw new IllegalArgumentException(action);
        }
    }
}
