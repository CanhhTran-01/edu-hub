package com.eduhub.edu_hub.shared.systemconfig.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.application.facade.IdentityFacade;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.systemconfig.api.dto.request.UpdateSystemConfigsRequest;
import com.eduhub.edu_hub.shared.systemconfig.application.exception.SystemConfigException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.Jwt;

class SystemConfigAdministrationServiceTest {
    private final IdentityFacade identityFacade = mock(IdentityFacade.class);
    private final SystemConfigService configurations = mock(SystemConfigService.class);
    private final SystemConfigAdministrationService service =
            new SystemConfigAdministrationService(identityFacade, configurations);
    private final Jwt principal = principal("ACCESS");

    @Test
    void checksNullAndRestrictedPrincipalBeforeReadingAccountOrConfiguration() {
        assertError(() -> service.findConfigurations(null, null), ErrorCode.UNAUTHENTICATED);
        assertError(
                () -> service.updateConfigurations(principal("PASSWORD_CHANGE"), null), ErrorCode.FIRST_LOGIN_REQUIRED);
        verifyNoInteractions(identityFacade, configurations);
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "LECTURER", "ACADEMIC_ADMIN"})
    void deniesOtherCurrentDatabaseRolesOnBothReadAndWrite(String roleName) {
        when(identityFacade.requireCurrentUser(principal)).thenReturn(account(roleName));
        assertError(() -> service.findConfigurations(principal, null), ErrorCode.UNAUTHORIZED);
        assertError(() -> service.updateConfigurations(principal, null), ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(configurations);
    }

    @Test
    void delegatesOnlyAfterCheckingSystemAdministratorAndUsesServerActorId() {
        when(identityFacade.requireCurrentUser(principal)).thenReturn(account("SYSTEM_ADMIN"));
        var request = new UpdateSystemConfigsRequest(List.of());
        service.findConfigurations(principal, null);
        service.updateConfigurations(principal, request);
        verify(configurations).findConfigurations(null);
        verify(configurations).updateConfigurations(request, 7L);
        verify(identityFacade, times(2)).requireCurrentUser(principal);
    }

    private UserInfoResponse account(String roleName) {
        return UserInfoResponse.builder().id(7L).status("ACTIVE").role(roleName).build();
    }

    private Jwt principal(String purpose) {
        return Jwt.withTokenValue("unit-token")
                .header("alg", "HS256")
                .subject("admin")
                .claim("token_use", purpose)
                .claim("uid", 7L)
                .claim("sid", 9L)
                .build();
    }

    private void assertError(Runnable operation, ErrorCode expectedError) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(SystemConfigException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(expectedError));
    }
}
