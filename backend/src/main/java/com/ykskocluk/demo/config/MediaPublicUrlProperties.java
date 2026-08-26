package com.ykskocluk.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.media")
public record MediaPublicUrlProperties(String publicBaseUrl) {
    public MediaPublicUrlProperties {
        publicBaseUrl = trimTrailingSlash(publicBaseUrl);
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }
}
