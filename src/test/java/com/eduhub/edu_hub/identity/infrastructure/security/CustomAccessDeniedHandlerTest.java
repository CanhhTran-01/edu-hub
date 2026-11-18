package com.eduhub.edu_hub.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduhub.edu_hub.identity.domain.enums.AccessTokenPurpose;
import com.eduhub.edu_hub.shared.common.ApiResponseFactory;
import com.eduhub.edu_hub.shared.config.TimeConfig;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.json.JsonMapper;

class CustomAccessDeniedHandlerTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-18T17:30:00Z"),
            new TimeConfig().applicationClock().getZone());
    private final ApiResponseFactory apiResponseFactory = new ApiResponseFactory(CLOCK);

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACCESS", "PASSWORD_CHANGE"})
    void returnsFirstLoginErrorOnlyForRestrictedJwt(String purpose) throws Exception {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("student")
                .claim(AccessTokenPurpose.CLAIM_NAME, purpose)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        var response = new MockHttpServletResponse();
        var mapper = JsonMapper.builder().findAndAddModules().build();
        new CustomAccessDeniedHandler(apiResponseFactory, mapper)
                .handle(
                        new MockHttpServletRequest("GET", "/api/v1/students"),
                        response,
                        new AccessDeniedException("denied"));
        assertThat(response.getStatus()).isEqualTo(403);
        var body = mapper.readTree(response.getContentAsString());
        assertThat(body.get("code").asInt()).isEqualTo(purpose.equals("PASSWORD_CHANGE") ? 4006 : 4001);
        assertThat(body.get("path").asText()).isEqualTo("/api/v1/students");
        assertThat(body.get("timestamp").asText()).isEqualTo("2026-11-19T00:30:00");
    }
}
