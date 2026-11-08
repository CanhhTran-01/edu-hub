package com.eduhub.edu_hub.identity.api.exception;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.eduhub.edu_hub.identity.api.controller.AuthController;
import com.eduhub.edu_hub.identity.application.exception.IdentityAuthenticationException;
import com.eduhub.edu_hub.identity.application.exception.PasswordValidationException;
import com.eduhub.edu_hub.identity.application.service.AuthApplicationService;
import com.eduhub.edu_hub.shared.common.FieldErrorDetail;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import com.eduhub.edu_hub.shared.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class IdentityExceptionHandlerTest {
    private final AuthApplicationService authService = mock(AuthApplicationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new IdentityExceptionHandler(), new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticationFailureUsesModuleHandlerAndKeepsHttpContract() throws Exception {
        when(authService.login(any())).thenThrow(new IdentityAuthenticationException(ErrorCode.INVALID_CREDENTIALS));
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"account\",\"password\":\"incorrect\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4003))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"));
    }

    @Test
    void passwordValidationUsesModuleHandlerAndPreservesFieldErrors() throws Exception {
        Jwt authenticatedJwt = authenticate();
        doThrow(new PasswordValidationException(
                        List.of(new FieldErrorDetail("newPassword", "Mật khẩu phải có ít nhất 8 ký tự"))))
                .when(authService)
                .changePassword(eq(authenticatedJwt), any());
        mvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"short\",\"confirmPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"))
                .andExpect(jsonPath("$.errors[0].message").value("Mật khẩu phải có ít nhất 8 ký tự"));
    }

    @Test
    void missingPrincipalProducesModuleAuthenticationError() throws Exception {
        doThrow(new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED))
                .when(authService)
                .logout(isNull(), eq("refresh"));
        mvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(4000));
        verify(authService).logout(null, "refresh");
    }

    @Test
    void logoutUsesSecurityContextPrincipalInsteadOfParsingRequestHeader() throws Exception {
        Jwt authenticatedJwt = authenticate();
        mvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh\"}"))
                .andExpect(status().isOk());
        verify(authService).logout(same(authenticatedJwt), eq("refresh"));
    }

    @Test
    void rawBearerHeaderDoesNotSupplyPrincipalWithoutSecurityContext() throws Exception {
        doThrow(new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED))
                .when(authService)
                .logout(isNull(), eq("refresh"));
        mvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer not-authenticated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(4000));
        verify(authService).logout(null, "refresh");
    }

    private Jwt authenticate() {
        Jwt authenticatedJwt = Jwt.withTokenValue("unit-token")
                .header("alg", "HS256")
                .subject("account")
                .claim("uid", 7L)
                .claim("sid", 9L)
                .build();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(authenticatedJwt));
        SecurityContextHolder.setContext(context);
        return authenticatedJwt;
    }
}
