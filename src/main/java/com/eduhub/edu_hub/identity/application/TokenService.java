package com.eduhub.edu_hub.identity.application;

import com.eduhub.edu_hub.identity.domain.model.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

@Service
public class TokenService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private final byte[] signingKey;
    private final ObjectMapper objectMapper;

    public TokenService(@Value("${jwt.secret}") String encodedSigningKey, ObjectMapper objectMapper) {
        this.signingKey = Base64.getDecoder().decode(encodedSigningKey);
        if (signingKey.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 random bytes (Base64)");
        }
        this.objectMapper = objectMapper;
    }

    public String accessToken(User user, Long sessionId, Instant issuedAt, Instant expiresAt) {
        try {
            String header = encode(objectMapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            String payload = encode(objectMapper.writeValueAsBytes(Map.of(
                    "sub", user.getUsername(),
                    "uid", user.getId(),
                    "sid", sessionId,
                    "role", user.getRole().getRoleName(),
                    "iat", issuedAt.getEpochSecond(),
                    "exp", expiresAt.getEpochSecond())));
            String signedPart = header + "." + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return signedPart + "." + encode(mac.doFinal(signedPart.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign access token", exception);
        }
    }

    public TokenClaims validateAccessToken(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) return null;
            String signedPart = parts[0] + "." + parts[1];
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            if (!MessageDigest.isEqual(mac.doFinal(signedPart.getBytes(StandardCharsets.US_ASCII)),
                    Base64.getUrlDecoder().decode(parts[2]))) return null;
            JsonNode header = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
            if (!"HS256".equals(header.path("alg").asText()) || !"JWT".equals(header.path("typ").asText())) {
                return null;
            }
            JsonNode claims = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            long expiry = claims.path("exp").asLong(0);
            long sessionId = claims.path("sid").asLong(0);
            long userId = claims.path("uid").asLong(0);
            if (expiry <= Instant.now().getEpochSecond() || sessionId <= 0 || userId <= 0) return null;
            return new TokenClaims(userId, sessionId, claims.path("sub").asText());
        } catch (Exception exception) {
            return null;
        }
    }

    public record TokenClaims(long userId, long sessionId, String username) {}

    public String newRefreshToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return encode(bytes);
    }

    public String hash(String token) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash refresh token", exception);
        }
    }

    private String encode(byte[] bytes) {
        return URL_ENCODER.encodeToString(bytes);
    }
}
