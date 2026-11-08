package com.eduhub.edu_hub.identity.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.identity.application.exception.IdentityAuthenticationException;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class TokenServiceTest {
    private final JwtDecoder jwtDecoder = mock(JwtDecoder.class);
    private final JwtEncoder jwtEncoder = mock(JwtEncoder.class);
    private final TokenService service = new TokenService(jwtEncoder, jwtDecoder);

    @Test
    void readsAuthenticatedPrincipalWithoutDecodingTokenAgain() {
        Jwt authenticatedJwt = Jwt.withTokenValue("unit-token")
                .header("alg", "HS256")
                .subject("account")
                .claim("uid", 7L)
                .claim("sid", 9L)
                .build();
        assertThat(service.requireAuthenticatedClaims(authenticatedJwt))
                .isEqualTo(new TokenService.TokenClaims(7L, 9L, "account"));
        verifyNoInteractions(jwtDecoder, jwtEncoder);
    }

    @Test
    void rejectsMissingPrincipalWhilePermitAllIsEnabled() {
        assertThatThrownBy(() -> service.requireAuthenticatedClaims(null))
                .isInstanceOfSatisfying(
                        IdentityAuthenticationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        verifyNoInteractions(jwtDecoder, jwtEncoder);
    }

    @Test
    void malformedPrincipalFailsWithAuthenticationErrorInsteadOfNullPointerException() {
        Jwt missingSessionId = Jwt.withTokenValue("unit-token")
                .header("alg", "HS256")
                .subject("account")
                .claim("uid", 7L)
                .build();
        assertThatThrownBy(() -> service.requireAuthenticatedClaims(missingSessionId))
                .isInstanceOfSatisfying(
                        IdentityAuthenticationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }
}
