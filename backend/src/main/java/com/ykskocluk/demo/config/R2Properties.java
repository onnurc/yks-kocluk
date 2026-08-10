package com.ykskocluk.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.r2")
public record R2Properties(
        boolean enabled,
        String accountId,
        String accessKeyId,
        String secretAccessKey,
        String bucket,
        String endpoint,
        String publicBaseUrl,
        int uploadUrlExpirationMinutes,
        int downloadUrlExpirationMinutes
) {
    public R2Properties {
        uploadUrlExpirationMinutes = positive(uploadUrlExpirationMinutes, 10, "upload URL expiration");
        downloadUrlExpirationMinutes = positive(downloadUrlExpirationMinutes, 10, "download URL expiration");
        if (enabled) {
            require(accountId, "account ID");
            require(accessKeyId, "access key ID");
            require(secretAccessKey, "secret access key");
            require(bucket, "bucket");
            require(endpoint, "endpoint");
        }
        publicBaseUrl = trimTrailingSlash(publicBaseUrl);
    }

    private static int positive(int value, int fallback, String name) {
        int result = value == 0 ? fallback : value;
        if (result < 1 || result > 60) throw new IllegalArgumentException("R2 " + name + " must be between 1 and 60 minutes");
        return result;
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("R2 " + name + " is required when R2 is enabled");
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }
}
