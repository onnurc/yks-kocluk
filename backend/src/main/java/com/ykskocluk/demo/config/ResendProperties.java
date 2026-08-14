package com.ykskocluk.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Resend settings bound from {@code app.resend.*}. {@code apiKey} is the secret (env only,
 * never committed — supplied via {@code RESEND_API_KEY} in application-local.yml/env);
 * {@code from} is the sender address (defaults to Resend's onboarding sender for dev; required
 * outside local/test/stub — see {@code ResendConfig}'s fail-fast guard). {@code replyTo} is
 * optional — when blank, {@code ResendMailClient} omits the field from the Resend request
 * entirely rather than sending an empty reply-to.
 */
@ConfigurationProperties(prefix = "app.resend")
public record ResendProperties(
        String apiKey,
        String from,
        String replyTo
) {
}
