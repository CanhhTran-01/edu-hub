package com.eduhub.edu_hub.identity.api.controller;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eduhub.edu_hub.identity.api.dto.request.LockUserRequest;
import com.eduhub.edu_hub.identity.api.dto.request.SearchUserAccountsRequest;
import com.eduhub.edu_hub.identity.api.dto.response.UserAccountDetailResponse;
import com.eduhub.edu_hub.identity.api.dto.response.UserAccountSummaryResponse;
import com.eduhub.edu_hub.identity.api.exception.IdentityExceptionHandler;
import com.eduhub.edu_hub.identity.application.exception.IdentityAccountException;
import com.eduhub.edu_hub.identity.application.exception.IdentityAuthenticationException;
import com.eduhub.edu_hub.identity.application.service.AdminUserService;
import com.eduhub.edu_hub.identity.domain.enums.RoleName;
import com.eduhub.edu_hub.identity.domain.enums.UserStatus;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.exception.GlobalExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminUserControllerTest {
    private final AdminUserService adminUserService = mock(AdminUserService.class);
    private final Jwt authenticatedJwt = Jwt.withTokenValue("unit-token")
            .header("alg", "HS256")
            .subject("admin")
            .claim("uid", 1L)
            .claim("sid", 3L)
            .build();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(authenticatedJwt));
        mvc = MockMvcBuilders.standaloneSetup(new AdminUserController(adminUserService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new IdentityExceptionHandler(), new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void searchUsesQueryDefaultsAndReturnsOnlyFourSummaryFields() throws Exception {
        var userAccount = UserAccountSummaryResponse.builder()
                .id(2L)
                .username("student")
                .role("STUDENT")
                .status("LOCKED")
                .build();
        when(adminUserService.searchUserAccounts(eq(authenticatedJwt), any()))
                .thenReturn(PageResponse.<UserAccountSummaryResponse>builder()
                        .content(List.of(userAccount))
                        .pageNumber(0)
                        .pageSize(20)
                        .totalElements(1)
                        .totalPages(1)
                        .isLast(true)
                        .build());

        mvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(2))
                .andExpect(jsonPath("$.data.content[0].username").value("student"))
                .andExpect(jsonPath("$.data.content[0].role").value("STUDENT"))
                .andExpect(jsonPath("$.data.content[0].status").value("LOCKED"))
                .andExpect(jsonPath("$.data.content[0]").value(aMapWithSize(4)))
                .andExpect(jsonPath("$.data.last").value(true));
        var requestCaptor = ArgumentCaptor.forClass(SearchUserAccountsRequest.class);
        verify(adminUserService).searchUserAccounts(same(authenticatedJwt), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getKeyword()).isEmpty();
        assertThat(requestCaptor.getValue().getPage()).isZero();
        assertThat(requestCaptor.getValue().getSize()).isEqualTo(20);
        assertThat(requestCaptor.getValue().getRole()).isNull();
        assertThat(requestCaptor.getValue().getStatus()).isNull();
        assertThat(requestCaptor.getValue().getFirstLogin()).isNull();
    }

    @Test
    void searchBindsExplicitQueryParameters() throws Exception {
        mvc.perform(get("/api/v1/admin/users")
                        .param("keyword", " student ")
                        .param("role", "LECTURER")
                        .param("status", "DISABLED")
                        .param("firstLogin", "false")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk());
        verify(adminUserService)
                .searchUserAccounts(
                        same(authenticatedJwt),
                        argThat(request -> request.getKeyword().equals(" student ")
                                && request.getRole() == RoleName.LECTURER
                                && request.getStatus() == UserStatus.DISABLED
                                && Boolean.FALSE.equals(request.getFirstLogin())
                                && request.getPage() == 2
                                && request.getSize() == 5));
    }

    @ParameterizedTest
    @ValueSource(strings = {"role", "status", "firstLogin"})
    void searchRejectsUnknownFilterValuesWithFieldErrors(String field) throws Exception {
        mvc.perform(get("/api/v1/admin/users").param(field, "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.errors[0].field").value(field));
        verifyNoInteractions(adminUserService);
    }

    @Test
    void detailReturnsAccountAndContactFieldsWithStableBooleanNames() throws Exception {
        var accountDetail = UserAccountDetailResponse.builder()
                .id(2L)
                .username("lecturer")
                .role("LECTURER")
                .status("ACTIVE")
                .failedLoginCount(5)
                .lockedUntil(LocalDateTime.of(2026, 11, 10, 10, 15))
                .passwordChangedAt(LocalDateTime.of(2026, 11, 1, 10, 0))
                .createdAt(LocalDateTime.of(2026, 10, 1, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 11, 2, 10, 0))
                .email("lecturer@example.com")
                .firstLogin(true)
                .temporarilyLocked(true)
                .fullName("Nguyễn Văn An")
                .phone("0901234567")
                .build();
        when(adminUserService.getUserAccountDetail(authenticatedJwt, 2L)).thenReturn(accountDetail);

        mvc.perform(get("/api/v1/admin/users/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(aMapWithSize(14)))
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.username").value("lecturer"))
                .andExpect(jsonPath("$.data.role").value("LECTURER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.failedLoginCount").value(5))
                .andExpect(jsonPath("$.data.lockedUntil").value("2026-11-10T10:15:00"))
                .andExpect(jsonPath("$.data.passwordChangedAt").value("2026-11-01T10:00:00"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-10-01T10:00:00"))
                .andExpect(jsonPath("$.data.updatedAt").value("2026-11-02T10:00:00"))
                .andExpect(jsonPath("$.data.email").value("lecturer@example.com"))
                .andExpect(jsonPath("$.data.firstLogin").value(true))
                .andExpect(jsonPath("$.data.temporarilyLocked").value(true))
                .andExpect(jsonPath("$.data.fullName").value("Nguyễn Văn An"))
                .andExpect(jsonPath("$.data.phone").value("0901234567"));
        verify(adminUserService).getUserAccountDetail(same(authenticatedJwt), eq(2L));
    }

    @Test
    void detailKeepsNullableFieldsWhenThereIsNoProfile() throws Exception {
        when(adminUserService.getUserAccountDetail(authenticatedJwt, 2L))
                .thenReturn(UserAccountDetailResponse.builder()
                        .id(2L)
                        .username("admin")
                        .role("SYSTEM_ADMIN")
                        .status("ACTIVE")
                        .build());

        mvc.perform(get("/api/v1/admin/users/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(aMapWithSize(14)))
                .andExpect(jsonPath("$.data.fullName").hasJsonPath())
                .andExpect(jsonPath("$.data.phone").hasJsonPath())
                .andExpect(jsonPath("$.data.email").hasJsonPath())
                .andExpect(jsonPath("$.data.firstLogin").value(false))
                .andExpect(jsonPath("$.data.temporarilyLocked").value(false));
    }

    @Test
    void missingAccountDetailUsesUserNotFoundContract() throws Exception {
        when(adminUserService.getUserAccountDetail(authenticatedJwt, 2L))
                .thenThrow(new IdentityAccountException(ErrorCode.USER_NOT_FOUND));

        mvc.perform(get("/api/v1/admin/users/2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4002));
    }

    @Test
    void detailDoesNotReplaceMissingPrincipalWithRawHeader() throws Exception {
        SecurityContextHolder.clearContext();
        when(adminUserService.getUserAccountDetail(null, 2L))
                .thenThrow(new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED));

        mvc.perform(get("/api/v1/admin/users/2").header("Authorization", "Bearer unverified-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(4000));
        verify(adminUserService).getUserAccountDetail(null, 2L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "size"})
    void searchRejectsInvalidPaginationWithFieldErrors(String field) throws Exception {
        mvc.perform(get("/api/v1/admin/users").param(field, field.equals("page") ? "-1" : "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.errors[0].field").value(field));
        verifyNoInteractions(adminUserService);
    }

    @Test
    void lockKeepsTargetIdInPathAndPassesNormalizedReasonDto() throws Exception {
        mvc.perform(post("/api/v1/admin/users/2/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\" Kiểm tra sự cố \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã khóa tài khoản"))
                .andExpect(jsonPath("$.data").doesNotExist());
        verify(adminUserService)
                .lockUserAccount(same(authenticatedJwt), eq(2L), eq(new LockUserRequest("Kiểm tra sự cố")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"reason\":null}", "{\"reason\":\"   \"}"})
    void lockRejectsMissingOrBlankReasonWithFieldError(String body) throws Exception {
        mvc.perform(post("/api/v1/admin/users/2/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
        verifyNoInteractions(adminUserService);
    }

    @Test
    void lockRejectsReasonExceedingLimit() throws Exception {
        mvc.perform(post("/api/v1/admin/users/2/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + "a".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
        verifyNoInteractions(adminUserService);
    }

    @Test
    void unlockAndResetKeepRoutesMessagesAndBodylessContract() throws Exception {
        mvc.perform(post("/api/v1/admin/users/2/unlock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã mở khóa tài khoản"));
        mvc.perform(post("/api/v1/admin/users/2/reset-password"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã đặt lại mật khẩu và gửi email"))
                .andExpect(jsonPath("$.data").doesNotExist());
        verify(adminUserService).unlockUserAccount(same(authenticatedJwt), eq(2L));
        verify(adminUserService).resetUserPassword(same(authenticatedJwt), eq(2L));
    }

    @Test
    void missingPrincipalIsNotReplacedByRawHeader() throws Exception {
        SecurityContextHolder.clearContext();
        doThrow(new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED))
                .when(adminUserService)
                .unlockUserAccount(null, 2L);
        mvc.perform(post("/api/v1/admin/users/2/unlock").header("Authorization", "Bearer unverified-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(4000));
        verify(adminUserService).unlockUserAccount(null, 2L);
    }

    @Test
    void disabledTargetUsesModuleErrorContract() throws Exception {
        doThrow(new IdentityAccountException(ErrorCode.DISABLED_ACCOUNT_OPERATION_NOT_ALLOWED))
                .when(adminUserService)
                .unlockUserAccount(authenticatedJwt, 2L);
        mvc.perform(post("/api/v1/admin/users/2/unlock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4010));
    }

    @Test
    void failedResetEmailKeepsGatewayErrorContract() throws Exception {
        doThrow(new IdentityAccountException(ErrorCode.PASSWORD_EMAIL_FAILED))
                .when(adminUserService)
                .resetUserPassword(authenticatedJwt, 2L);
        mvc.perform(post("/api/v1/admin/users/2/reset-password"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(4008));
    }
}
