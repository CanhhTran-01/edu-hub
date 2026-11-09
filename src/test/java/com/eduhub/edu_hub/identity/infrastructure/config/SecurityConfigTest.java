package com.eduhub.edu_hub.identity.infrastructure.config;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.identity.application.service.TokenService;
import com.eduhub.edu_hub.identity.domain.enums.AccessTokenPurpose;
import com.eduhub.edu_hub.identity.domain.model.Role;
import com.eduhub.edu_hub.identity.domain.model.User;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/** Kiểm tra bean thuần Java, không khởi động Spring context hoặc kết nối database. */
class SecurityConfigTest {
    private final SecurityConfig configuration = new SecurityConfig();
    private final JwtEncoder encoder = configuration.jwtEncoder(configuration.jwtSigningKey(signingKey()));
    private final JwtDecoder decoder = configuration.jwtDecoder(configuration.jwtSigningKey(signingKey()));

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void authenticatesHs256AndAuthorizesOnlyOrdinaryJwtForBusinessApis(boolean firstLogin) {
        User account = account(firstLogin);
        Instant issuedAt = Instant.now();
        String encoded =
                new TokenService(encoder).generateAccessToken(account, 9L, issuedAt, issuedAt.plusSeconds(900));
        Jwt decoded = decoder.decode(encoded);
        var authentication = configuration
                .jwtAuthenticationConverter(configuration.jwtGrantedAuthoritiesConverter())
                .convert(decoded);
        var decision = configuration
                .applicationApiAuthorizationManager()
                .authorize(
                        () -> authentication,
                        new RequestAuthorizationContext(new MockHttpServletRequest("GET", "/api/v1/students")));
        assertThat(decision.isGranted()).isEqualTo(!firstLogin);
    }

    @Test
    void droppingTheBearerHeaderCannotBypassTheBusinessApiGate() {
        var anonymous = new AnonymousAuthenticationToken(
                "test", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        var decision = configuration
                .applicationApiAuthorizationManager()
                .authorize(
                        () -> anonymous,
                        new RequestAuthorizationContext(new MockHttpServletRequest("GET", "/api/v1/students")));
        assertThat(decision.isGranted()).isFalse();
    }

    @Test
    void accessTokenRemainsAcceptedAfterPasswordStateChangesUntilExpiry() {
        User account = account(false);
        Instant issuedAt = Instant.now();
        String encoded =
                new TokenService(encoder).generateAccessToken(account, 9L, issuedAt, issuedAt.plusSeconds(900));
        account.setFirstLogin(true);
        account.setPasswordHash("changed-password-hash");
        assertThat(decoder.decode(encoded).getClaimAsString(AccessTokenPurpose.CLAIM_NAME))
                .isEqualTo("ACCESS");
    }

    @Test
    void rejectsMissingOrUnknownPurposeAndExpiredJwt() {
        assertThatThrownBy(() -> decoder.decode(sign(null, Instant.now().plusSeconds(900))))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(sign("UNKNOWN", Instant.now().plusSeconds(900))))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(sign("ACCESS", Instant.now().minusSeconds(1))))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokensSignedWithAnotherKey() {
        String otherSigningKey = Base64.getEncoder()
                .encodeToString(
                        "another-unit-test-signing-key-32bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        JwtEncoder otherEncoder = configuration.jwtEncoder(configuration.jwtSigningKey(otherSigningKey));
        Instant issuedAt = Instant.now();
        String encoded = new TokenService(otherEncoder)
                .generateAccessToken(account(false), 9L, issuedAt, issuedAt.plusSeconds(900));
        assertThatThrownBy(() -> decoder.decode(encoded)).isInstanceOf(JwtException.class);
    }

    private String sign(String purpose, Instant expiresAt) {
        var claims = JwtClaimsSet.builder()
                .subject("student")
                .claim("uid", 7L)
                .claim("sid", 9L)
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(expiresAt);
        if (purpose != null) claims.claim(AccessTokenPurpose.CLAIM_NAME, purpose);
        return encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims.build()))
                .getTokenValue();
    }

    private User account(boolean firstLogin) {
        User account = new User();
        account.setId(7L);
        account.setUsername("student");
        account.setFirstLogin(firstLogin);
        Role role = new Role();
        role.setRoleName("STUDENT");
        account.setRole(role);
        return account;
    }

    private String signingKey() {
        return Base64.getEncoder().encodeToString(new byte[32]);
    }
}
