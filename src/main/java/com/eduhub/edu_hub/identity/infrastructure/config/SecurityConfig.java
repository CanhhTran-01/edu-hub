package com.eduhub.edu_hub.identity.infrastructure.config;

import com.eduhub.edu_hub.identity.application.exception.IdentityConfigurationException;
import com.eduhub.edu_hub.identity.domain.enums.AccessTokenPurpose;
import com.eduhub.edu_hub.identity.domain.policy.PasswordPolicy;
import com.eduhub.edu_hub.identity.infrastructure.security.CustomAccessDeniedHandler;
import com.eduhub.edu_hub.identity.infrastructure.security.CustomAuthenticationEntryPoint;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CustomAuthenticationEntryPoint authenticationEntryPoint,
            CustomAccessDeniedHandler accessDeniedHandler,
            JwtDecoder jwtDecoder,
            JwtAuthenticationConverter authenticationConverter,
            AuthorizationManager<RequestAuthorizationContext> applicationApiAuthorizationManager) {

        return http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(authenticationConverter)))

                // phải đăng nhập trước để test API nghiệp vụ, hiện chưa phân quyền theo role
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/change-password", "/api/v1/auth/logout")
                        .authenticated()

                        // TODO(AUTHORIZATION): Chốt ma trận quyền role cho từng API; hiện chưa kiểm tra role.
                        .requestMatchers("/api/v1/**")
                        .access(applicationApiAuthorizationManager)

                        .anyRequest()
                        .permitAll()) // phía dưới cùng không ghi đè rule đã khớp
                .build();
    }

    @Bean
    AuthorizationManager<RequestAuthorizationContext> applicationApiAuthorizationManager() {
        return (authentication, context) -> {
            var authenticated = authentication.get();
            return new AuthorizationDecision(authenticated instanceof JwtAuthenticationToken jwtAuthentication
                    && jwtAuthentication.isAuthenticated()
                    && AccessTokenPurpose.ACCESS
                            .name()
                            .equals(jwtAuthentication.getToken().getClaim(AccessTokenPurpose.CLAIM_NAME)));
        };
    }

    @Bean
    PasswordPolicy passwordPolicy(PasswordSettings settings) {
        return new PasswordPolicy(settings.getMinimumLength());
    }

    @Bean
    JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("role");
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        return authoritiesConverter;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter(JwtGrantedAuthoritiesConverter authoritiesConverter) {
        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return authenticationConverter;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecretKey jwtSigningKey(@Value("${jwt.secret}") String encodedSigningKey) {
        byte[] signingKey;
        try {
            signingKey = Base64.getDecoder().decode(encodedSigningKey);
        } catch (IllegalArgumentException exception) {
            throw new IdentityConfigurationException("JWT_SECRET phải có định dạng Base64 hợp lệ", exception);
        }
        if (signingKey.length < 32) {
            throw new IdentityConfigurationException(
                    "JWT_SECRET phải chứa ít nhất 32 byte ngẫu nhiên được mã hóa Base64");
        }
        return new SecretKeySpec(signingKey, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        // Giữ thời điểm hết hạn nghiêm ngặt như token cũ, không thêm khoảng dung sai mặc định.
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(Duration.ZERO), token -> {
                    if (token.getExpiresAt() == null
                            || token.getSubject() == null
                            || token.getSubject().isBlank()
                            || !(token.getClaim("uid") instanceof Number userId)
                            || userId.longValue() <= 0
                            || !(token.getClaim("sid") instanceof Number sessionId)
                            || sessionId.longValue() <= 0
                            || !"JWT".equals(token.getHeaders().get("typ"))
                            || (!AccessTokenPurpose.ACCESS.name().equals(token.getClaim(AccessTokenPurpose.CLAIM_NAME))
                                    && !AccessTokenPurpose.PASSWORD_CHANGE
                                            .name()
                                            .equals(token.getClaim(AccessTokenPurpose.CLAIM_NAME)))) {
                        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
                    }

                    if (!token.getExpiresAt().isAfter(Instant.now())) {
                        return OAuth2TokenValidatorResult.failure(
                                new OAuth2Error(CustomAuthenticationEntryPoint.ACCESS_TOKEN_EXPIRED));
                    }

                    return OAuth2TokenValidatorResult.success();
                }));

        return decoder;
    }
}
