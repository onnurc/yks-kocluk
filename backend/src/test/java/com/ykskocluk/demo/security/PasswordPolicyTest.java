package com.ykskocluk.demo.security;

import com.ykskocluk.demo.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class PasswordPolicyTest {

    @Test
    void acceptsTwelveThroughSeventyTwoCharactersWithoutComplexityRules() {
        assertThatCode(() -> PasswordPolicy.validate("twelve chars"))
                .doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.validate("a".repeat(72)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsTooShortTooLongAndClearlyCommonPasswords() {
        assertViolation("a".repeat(11));
        assertViolation("a".repeat(73));
        assertViolation("  PASSWORD123  ");
    }

    private void assertViolation(String password) {
        ApiException error = catchThrowableOfType(ApiException.class,
                () -> PasswordPolicy.validate(password));
        assertThat(error.getErrorCode()).isEqualTo("PASSWORD_POLICY_VIOLATION");
    }
}
