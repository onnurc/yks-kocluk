package com.ykskocluk.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT settings bound from {@code app.jwt.*}. {@code secret} is the HS256 signing key
 * (>= 32 bytes); supplied via env/application-local.yml, never committed.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        Duration accessTtl,
        Duration refreshTtl
) {
}
