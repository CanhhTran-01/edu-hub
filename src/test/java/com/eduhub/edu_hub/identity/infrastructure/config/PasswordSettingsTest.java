package com.eduhub.edu_hub.identity.infrastructure.config;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.identity.application.exception.IdentityConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordSettingsTest {
    @Test
    void exemptsSystemGeneratedPasswordFromStrengthRulesButEnforcesBcryptByteLimit() {
        assertThat(new PasswordSettings(8, "1").getInitialPassword()).isEqualTo("1");
        assertThatCode(() -> new PasswordSettings(8, "á".repeat(36))).doesNotThrowAnyException();
        assertThatThrownBy(() -> new PasswordSettings(8, "á".repeat(37)))
                .isInstanceOf(IdentityConfigurationException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsEmptyInitialPassword(String initialPassword) {
        assertThatThrownBy(() -> new PasswordSettings(8, initialPassword))
                .isInstanceOf(IdentityConfigurationException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 7, 73})
    void rejectsUnsupportedMinimumLength(int minimumLength) {
        assertThatThrownBy(() -> new PasswordSettings(minimumLength, "temporary"))
                .isInstanceOf(IdentityConfigurationException.class);
    }
}
