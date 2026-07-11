package com.ykskocluk.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the real Iyzico sandbox client bound from {@code payments.iyzico.*}.
 */
@ConfigurationProperties(prefix = "payments.iyzico")
public record IyzicoProperties(
        boolean enabled,
        String mode,
        String apiKey,
        String secretKey,
        String baseUrl,
        String callbackUrl
) {
}
