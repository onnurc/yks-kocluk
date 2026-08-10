package com.ykskocluk.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.media")
public record MediaPolicyProperties(long profileImageMaxBytes, long coachVideoMaxBytes, long documentMaxBytes) {
    public MediaPolicyProperties {
        profileImageMaxBytes = defaultIfZero(profileImageMaxBytes, 5L * 1024 * 1024);
        coachVideoMaxBytes = defaultIfZero(coachVideoMaxBytes, 20L * 1024 * 1024);
        documentMaxBytes = defaultIfZero(documentMaxBytes, 10L * 1024 * 1024);
        if (profileImageMaxBytes < 1 || coachVideoMaxBytes < 1 || documentMaxBytes < 1) {
            throw new IllegalArgumentException("Media size limits must be positive");
        }
    }

    private static long defaultIfZero(long value, long fallback) { return value == 0 ? fallback : value; }
}
