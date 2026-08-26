package com.ykskocluk.demo.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaConfigurationValidatorTest {
    @Test
    void activeLocalProfileWithR2MayUseLocalhost() {
        assertThatCode(() -> validator(true, "local", "http://localhost:8080").validate())
                .doesNotThrowAnyException();
    }

    @Test
    void nonLocalR2RejectsMissingPublicBaseUrl() {
        assertThatThrownBy(() -> validator(true, "production", null).validate())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MEDIA_PUBLIC_BASE_URL");
    }

    @Test
    void nonLocalR2RejectsLocalhostPublicBaseUrl() {
        assertThatThrownBy(() -> validator(true, "production", "http://localhost:8080").validate())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("non-local absolute HTTPS origin");
    }

    @Test
    void nonLocalR2RejectsRemoteHttpPublicBaseUrl() {
        assertThatThrownBy(() -> validator(true, "production", "http://api.uniform.example").validate())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("non-local absolute HTTPS origin");
    }

    @Test
    void nonLocalR2AcceptsConfiguredHttpsOrigin() {
        assertThatCode(() -> validator(true, "production", "https://api.uniform.example/").validate())
                .doesNotThrowAnyException();
    }

    @Test
    void disabledR2DoesNotRequireExternalMediaOrigin() {
        assertThatCode(() -> validator(false, "production", null).validate()).doesNotThrowAnyException();
    }

    private MediaConfigurationValidator validator(boolean enabled, String activeProfile, String mediaBaseUrl) {
        R2Properties r2 = new R2Properties(enabled,
                enabled ? "account" : null,
                enabled ? "key" : null,
                enabled ? "secret" : null,
                enabled ? "bucket" : null,
                enabled ? "https://r2.example.com" : null,
                10, 10);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfile);
        return new MediaConfigurationValidator(r2, new MediaPublicUrlProperties(mediaBaseUrl), environment);
    }
}
