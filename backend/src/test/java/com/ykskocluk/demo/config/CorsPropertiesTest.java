package com.ykskocluk.demo.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorsPropertiesTest {

    @Test
    void normalizesMultipleExactOrigins() {
        CorsProperties properties = new CorsProperties(List.of(
                " http://localhost:5173 ", "https://frontend.example.com",
                "https://frontend.example.com"));

        assertThat(properties.allowedOrigins()).containsExactly(
                "http://localhost:5173", "https://frontend.example.com");
    }

    @Test
    void wildcardIsRejectedByExactOriginPolicy() {
        assertThatThrownBy(() -> new CorsProperties(List.of("*")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wildcard");
    }
}
