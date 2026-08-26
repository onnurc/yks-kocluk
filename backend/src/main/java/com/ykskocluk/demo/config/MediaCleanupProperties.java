package com.ykskocluk.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.media.cleanup")
public record MediaCleanupProperties(Duration pendingUploadExpiration, int batchSize) {
    public MediaCleanupProperties {
        pendingUploadExpiration = pendingUploadExpiration == null ? Duration.ofHours(24) : pendingUploadExpiration;
        batchSize = batchSize == 0 ? 100 : batchSize;
        if (pendingUploadExpiration.isNegative() || pendingUploadExpiration.isZero()) {
            throw new IllegalArgumentException("Pending media upload expiration must be positive");
        }
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("Pending media cleanup batch size must be between 1 and 1000");
        }
    }
}
