package com.eduhub.edu_hub.identity.api.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eduhub.edu_hub.identity.api.dto.request.LockUserRequest;
import com.eduhub.edu_hub.identity.api.dto.request.SearchUserAccountsRequest;
import com.eduhub.edu_hub.identity.api.dto.response.UserInfoResponse;
import com.eduhub.edu_hub.identity.api.exception.IdentityExceptionHandler;
import com.eduhub.edu_hub.identity.application.exception.IdentityAccountException;
import com.eduhub.edu_hub.identity.application.exception.IdentityAuthenticationException;
import com.eduhub.edu_hub.identity.application.service.AdminUserService;
import com.eduhub.edu_hub.shared.common.PageResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.exception.GlobalExceptionHandler;
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
    void searchUsesExistingQueryDefaultsAndReturnsCurrentAccountContract() throws Exception {
        var userAccount = UserInfoResponse.builder()
                .id(2L)
                .username("student")
                .email("student@example.com")
                .role("STUDENT")
                .status("LOCKED")
                .isFirstLogin(true)
                .build();
        when(adminUserService.searchUserAccounts(eq(authenticatedJwt), any()))
                .thenReturn(PageResponse.<UserInfoResponse>builder()
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
                .andExpect(jsonPath("$.data.content[0].status").value("LOCKED"))
                .andExpect(jsonPath("$.data.content[0].firstLogin").value(true))
                .andExpect(jsonPath("$.data.last").value(true));
        var requestCaptor = ArgumentCaptor.forClass(SearchUserAccountsRequest.class);
        verify(adminUserService).searchUserAccounts(same(authenticatedJwt), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getKeyword()).isEmpty();
        assertThat(requestCaptor.getValue().getPage()).isZero();
        assertThat(requestCaptor.getValue().getSize()).isEqualTo(20);
    }

    @Test
    void searchBindsExplicitQueryParameters() throws Exception {
        mvc.perform(get("/api/v1/admin/users")
                        .param("keyword", " student ")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk());
        verify(adminUserService)
                .searchUserAccounts(
                        same(authenticatedJwt),
                        argThat(request -> request.getKeyword().equals(" student ")
                                && request.getPage() == 2
                                && request.getSize() == 5));
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
