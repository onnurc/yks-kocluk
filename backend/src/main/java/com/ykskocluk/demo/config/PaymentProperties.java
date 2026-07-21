package com.ykskocluk.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Payment settings bound from {@code app.payment.*}. {@code commissionRate} is a fraction
 * (e.g. {@code 0.20} = 20%) snapshotted onto each {@code Payment} at charge time; {@code retryDays}
 * is the number of daily charge attempts in the PAST_DUE grace window before a sub EXPIRES;
 * {@code pendingCheckoutTimeoutMinutes} is how long a subscription may stay PENDING_PAYMENT
 * (unpaid checkout) before the renewal job expires it and frees the student to re-subscribe.
 */
@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(
        BigDecimal commissionRate,
        int retryDays,
        int pendingCheckoutTimeoutMinutes
) {
}
