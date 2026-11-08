package com.eduhub.edu_hub.identity.infrastructure.security;

import com.eduhub.edu_hub.identity.domain.enums.AccessTokenPurpose;
import com.eduhub.edu_hub.shared.common.ApiResponse;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {
    private final ObjectMapper objectMapper;

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, @NonNull AccessDeniedException exception)
            throws IOException {

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        var authentication = SecurityContextHolder.getContext().getAuthentication();

        ErrorCode error = authentication instanceof JwtAuthenticationToken jwtAuthentication
                        && AccessTokenPurpose.PASSWORD_CHANGE
                                .name()
                                .equals(jwtAuthentication.getToken().getClaim(AccessTokenPurpose.CLAIM_NAME))
                ? ErrorCode.FIRST_LOGIN_REQUIRED
                : ErrorCode.UNAUTHORIZED;

        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(error, request.getRequestURI())));
    }
}
