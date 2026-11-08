package com.eduhub.edu_hub.identity.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eduhub.edu_hub.identity.application.exception.PasswordValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {
    private final PasswordPolicy validator = new PasswordPolicy(8);

    @Test
    void acceptsValidNewPassword() {
        assertThatCode(() -> validator.validate("NewPass1!", "NewPass1!", "student"))
                .doesNotThrowAnyException();
    }

    @Test
    void reportsEveryFailedCondition() {
        assertThatThrownBy(() -> validator.validate("abc ", "different", "student"))
                .isInstanceOfSatisfying(PasswordValidationException.class, exception -> {
                    assertThat(exception.getErrors()).hasSize(6);
                    assertThat(exception.getErrors()).extracting("field").contains("newPassword", "confirmPassword");
                });
    }

    @Test
    void rejectsExactUsernameButKeepsCaseSensitiveComparison() {
        assertThatThrownBy(() -> validator.validate("Student1!", "Student1!", "Student1!"))
                .isInstanceOf(PasswordValidationException.class);
        assertThatCode(() -> validator.validate("Student1!", "Student1!", "student1!"))
                .doesNotThrowAnyException();
    }

    @Test
    void usesConfiguredMinimumLength() {
        assertThatThrownBy(() -> new PasswordPolicy(12).validate("NewPass1!", "NewPass1!", "student"))
                .isInstanceOf(PasswordValidationException.class);
    }

    @Test
    void enforcesBcryptByteBoundaryForAsciiAndUnicode() {
        String asciiBoundary = "Aa1!" + "b".repeat(68);
        String unicodeBoundary = "Aa1!" + "á".repeat(34);
        assertThatCode(() -> validator.validate(asciiBoundary, asciiBoundary, "student"))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(unicodeBoundary, unicodeBoundary, "student"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(asciiBoundary + "b", asciiBoundary + "b", "student"))
                .isInstanceOf(PasswordValidationException.class);
        assertThatThrownBy(() -> validator.validate(unicodeBoundary + "á", unicodeBoundary + "á", "student"))
                .isInstanceOf(PasswordValidationException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void missingCurrentPasswordIsAFieldError(String currentPassword) {
        assertThatThrownBy(() -> validator.validateCurrentPasswordInput(currentPassword))
                .isInstanceOfSatisfying(
                        PasswordValidationException.class, exception -> assertThat(exception.getErrors())
                                .extracting("field")
                                .containsExactly("currentPassword"));
    }

    @Test
    void rejectsOversizedCurrentPasswordBeforeBcrypt() {
        assertThatThrownBy(() -> validator.validateCurrentPasswordInput("á".repeat(37)))
                .isInstanceOf(PasswordValidationException.class);
    }

    @Test
    void limitsReuseChecksToCurrentAndOnePreviousOfficialPassword() {
        assertThatCode(() -> validator.validatePasswordReuse(false, false)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validatePasswordReuse(true, false))
                .isInstanceOf(PasswordValidationException.class);
        assertThatThrownBy(() -> validator.validatePasswordReuse(false, true))
                .isInstanceOf(PasswordValidationException.class);
    }
}
