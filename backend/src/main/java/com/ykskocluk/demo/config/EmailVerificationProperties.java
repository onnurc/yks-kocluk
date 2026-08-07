package com.ykskocluk.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.email-verification")
public record EmailVerificationProperties(Duration codeTtl, Duration resendCooldown,
                                          Duration retention, int maxAttempts) {
}
