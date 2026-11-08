package com.eduhub.edu_hub.identity.application.service;

import com.eduhub.edu_hub.identity.application.exception.IdentityAuthenticationException;
import com.eduhub.edu_hub.identity.domain.model.User;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtEncodingException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    public String generateAccessToken(User user, Long sessionId, Instant issuedAt, Instant expiresAt) {
        // TODO(FIRST_LOGIN): Khi refactor đổi mật khẩu, phát JWT hạn chế cho tài khoản first-login và chặn API nghiệp
        // vụ.
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("sid", sessionId)
                .claim("role", user.getRole().getRoleName())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        try {
            return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        } catch (JwtEncodingException exception) {
            throw new IdentityAuthenticationException(ErrorCode.UNEXPECTED_ERROR, exception);
        }
    }

    /** Đường tương thích của hồ sơ cá nhân qua IdentityFacade; controller Identity dùng principal đã xác thực. */
    public TokenClaims validateAccessToken(String token) {
        try {
            return requireAuthenticatedClaims(jwtDecoder.decode(token));
        } catch (JwtException | IllegalArgumentException | IdentityAuthenticationException exception) {
            return null;
        }
    }

    /**
     * Đọc danh tính từ Jwt đã được Resource Server xác thực, không gọi decoder lần nữa.
     * Jwt phải lấy từ SecurityContext, không dựng từ body hoặc JWT chưa kiểm tra chữ ký.
     * Khi permitAll chưa bắt buộc đăng nhập, principal có thể null và phải trả lỗi 401.
     */
    public TokenClaims requireAuthenticatedClaims(Jwt authenticatedJwt) {
        if (authenticatedJwt == null
                || authenticatedJwt.getSubject() == null
                || authenticatedJwt.getSubject().isBlank()
                || !(authenticatedJwt.getClaim("uid") instanceof Number userId)
                || userId.longValue() <= 0
                || !(authenticatedJwt.getClaim("sid") instanceof Number sessionId)
                || sessionId.longValue() <= 0) {
            throw new IdentityAuthenticationException(ErrorCode.UNAUTHENTICATED);
        }
        return new TokenClaims(userId.longValue(), sessionId.longValue(), authenticatedJwt.getSubject());
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
            throw new IdentityAuthenticationException(ErrorCode.UNEXPECTED_ERROR, exception);
        }
    }

    private String encode(byte[] bytes) {
        return URL_ENCODER.encodeToString(bytes);
    }
}
