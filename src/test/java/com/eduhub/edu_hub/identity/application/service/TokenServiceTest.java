package com.eduhub.edu_hub.identity.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eduhub.edu_hub.identity.application.exception.IdentityAuthenticationException;
import com.eduhub.edu_hub.identity.domain.enums.AccessTokenPurpose;
import com.eduhub.edu_hub.identity.domain.model.Role;
import com.eduhub.edu_hub.identity.domain.model.User;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

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
    void rejectsMissingPrincipalAtServiceBoundary() {
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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void issuesRestrictedPurposeWithoutBusinessAuthoritiesForFirstLogin(boolean firstLogin) {
        User account = new User();
        account.setId(7L);
        account.setUsername("student");
        account.setFirstLogin(firstLogin);
        Role role = new Role();
        role.setRoleName("STUDENT");
        account.setRole(role);
        when(jwtEncoder.encode(any()))
                .thenReturn(Jwt.withTokenValue("signed-token")
                        .header("alg", "HS256")
                        .subject("student")
                        .build());
        Instant issuedAt = Instant.now();
        assertThat(service.generateAccessToken(account, 9L, issuedAt, issuedAt.plusSeconds(900)))
                .isEqualTo("signed-token");
        var parameters = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(parameters.capture());
        var claims = parameters.getValue().getClaims();
        assertThat(claims.getClaimAsString(AccessTokenPurpose.CLAIM_NAME))
                .isEqualTo(firstLogin ? "PASSWORD_CHANGE" : "ACCESS");
        assertThat(claims.getClaims()).containsEntry("uid", 7L).containsEntry("sid", 9L);
        assertThat(claims.getSubject()).isEqualTo("student");
        if (firstLogin) assertThat(claims.getClaims()).doesNotContainKey("role");
        else assertThat(claims.getClaimAsString("role")).isEqualTo("STUDENT");
        assertThat(parameters.getValue().getJwsHeader().getAlgorithm().getName())
                .isEqualTo("HS256");
    }
}
