package com.ykskocluk.demo.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables {@link PaymentProperties} (commission rate / retry window). Not profile-gated — the
 * commission snapshot applies in every profile, including {@code test} with the stub iyzico client.
 */
@Configuration
@EnableConfigurationProperties(PaymentProperties.class)
public class PaymentConfig {
}
