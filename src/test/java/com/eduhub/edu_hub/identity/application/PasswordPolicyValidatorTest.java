package com.eduhub.edu_hub.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordPolicyValidatorTest {
    private final PasswordPolicyValidator validator = new PasswordPolicyValidator();

    @Test
    void acceptsValidNewPassword() {
        assertThatCode(() -> validator.validate("NewPass1!", "NewPass1!", false))
                .doesNotThrowAnyException();
    }

    @Test
    void reportsEveryFailedCondition() {
        assertThatThrownBy(() -> validator.validate("abc ", "different", true))
                .isInstanceOfSatisfying(PasswordValidationException.class, exception -> {
                    assertThat(exception.getErrors()).hasSize(7);
                    assertThat(exception.getErrors()).extracting("field").contains("newPassword", "confirmPassword");
                });
    }
}
