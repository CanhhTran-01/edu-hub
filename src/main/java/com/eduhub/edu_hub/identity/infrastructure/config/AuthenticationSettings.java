package com.eduhub.edu_hub.identity.infrastructure.config;

import com.eduhub.edu_hub.identity.application.exception.IdentityConfigurationException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record AuthenticationSettings(
        @Value("${identity.authentication.max-failed-logins}") int maxFailedLogins,
        @Value("${identity.authentication.temporary-lock-duration}") Duration temporaryLockDuration,
        @Value("${jwt.access-token-lifetime}") Duration accessTokenLifetime,
        @Value("${jwt.refresh-token-lifetime}") Duration refreshTokenLifetime) {

    public AuthenticationSettings {
        if (maxFailedLogins < 1
                || temporaryLockDuration == null
                || temporaryLockDuration.isNegative()
                || temporaryLockDuration.isZero()
                || accessTokenLifetime == null
                || accessTokenLifetime.isNegative()
                || accessTokenLifetime.isZero()
                || refreshTokenLifetime == null
                || refreshTokenLifetime.isNegative()
                || refreshTokenLifetime.isZero()) {
            throw new IdentityConfigurationException("Số lần đăng nhập sai và các thời hạn xác thực phải lớn hơn 0");
        }
    }

}
