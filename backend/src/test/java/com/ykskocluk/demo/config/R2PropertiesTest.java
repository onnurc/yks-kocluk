package com.ykskocluk.demo.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class R2PropertiesTest {
    @Test void disabledNeedsNoCredentialsAndUsesDefaultExpirations() {
        R2Properties p = new R2Properties(false, null, null, null, null, null, 0, 0);
        assertThat(p.uploadUrlExpirationMinutes()).isEqualTo(10);
        assertThat(p.downloadUrlExpirationMinutes()).isEqualTo(10);
    }

    @Test void enabledRejectsMissingConfiguration() {
        assertThatThrownBy(() -> new R2Properties(true, null, null, null, null, null, 10, 10))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("account ID");
    }

    @Test void bindsValuesAndExpirationSettings() {
        R2Properties p = new R2Properties(true, "account", "key", "secret", "bucket",
                "https://account.r2.cloudflarestorage.com", 7, 8);
        assertThat(p.uploadUrlExpirationMinutes()).isEqualTo(7);
        assertThat(p.downloadUrlExpirationMinutes()).isEqualTo(8);
    }
}
