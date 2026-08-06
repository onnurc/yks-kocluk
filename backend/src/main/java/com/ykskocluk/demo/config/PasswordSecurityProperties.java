package com.ykskocluk.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.password-security")
public record PasswordSecurityProperties(String frontendBaseUrl, Duration resetTokenTtl,
                                         Duration changeCooldown, Duration tokenRetention) {}
