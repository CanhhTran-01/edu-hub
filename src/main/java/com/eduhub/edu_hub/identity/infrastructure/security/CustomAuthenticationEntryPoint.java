package com.eduhub.edu_hub.identity.infrastructure.security;

import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    public static final String ACCESS_TOKEN_EXPIRED = "access_token_expired";
    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull AuthenticationException exception)
            throws IOException {

        ErrorCode errorCode = ErrorCode.UNAUTHENTICATED;

        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof JwtValidationException validationException
                    && validationException.getErrors().stream()
                            .anyMatch(error -> ACCESS_TOKEN_EXPIRED.equals(error.getErrorCode()))) {
                errorCode = ErrorCode.SESSION_EXPIRED;
                break;
            }
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("WWW-Authenticate", "Bearer");
        response.getWriter()
                .write(objectMapper.writeValueAsString(ApiResponse.error(errorCode, request.getRequestURI())));
    }
}
