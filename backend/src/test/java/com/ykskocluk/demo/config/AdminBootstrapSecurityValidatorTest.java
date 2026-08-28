package com.ykskocluk.demo.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminBootstrapSecurityValidatorTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    @Test
    void localProfileAllowsExplicitDevelopmentBootstrapBehavior() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        assertThatCode(() -> validator(environment).validate()).doesNotThrowAnyException();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void nonLocalMissingOrPlaceholderBootstrapFailsClosed() {
        assertThatThrownBy(() -> validator(new MockEnvironment()).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("explicit non-default BCrypt");

        MockEnvironment placeholder = new MockEnvironment()
                .withProperty("ADMIN_PASSWORD_HASH", "<ADMIN_PASSWORD_HASH>");
        assertThatThrownBy(() -> validator(placeholder).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("explicit non-default BCrypt");
    }

    @Test
    @SuppressWarnings("unchecked")
    void secureNonLocalConfigurationAndRotatedDatabaseCredentialAreAccepted() {
        String configured = new BCryptPasswordEncoder().encode("test-only secure bootstrap phrase");
        String stored = new BCryptPasswordEncoder().encode("test-only independently rotated phrase");
        MockEnvironment environment = new MockEnvironment().withProperty("ADMIN_PASSWORD_HASH", configured);
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class),
                any(Object[].class))).thenReturn(List.of(stored));

        assertThatCode(() -> validator(environment).validate()).doesNotThrowAnyException();
    }

    @Test
    @SuppressWarnings("unchecked")
    void historicalDatabaseCredentialCannotRemainValidOutsideLocal() {
        String configured = new BCryptPasswordEncoder().encode("test-only secure bootstrap phrase");
        MockEnvironment environment = new MockEnvironment().withProperty("ADMIN_PASSWORD_HASH", configured);
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class),
                any(Object[].class))).thenReturn(List.of(AdminBootstrapSecurityValidator.HISTORICAL_DEFAULT_HASH));

        assertThatThrownBy(() -> validator(environment).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be rotated");
    }

    private AdminBootstrapSecurityValidator validator(MockEnvironment environment) {
        return new AdminBootstrapSecurityValidator(environment, jdbcTemplate);
    }
}
