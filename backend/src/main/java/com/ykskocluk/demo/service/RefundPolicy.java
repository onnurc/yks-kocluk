package com.ykskocluk.demo.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/** Authoritative product-level refund window for successful plan charges. */
public final class RefundPolicy {
    public static final Duration WINDOW = Duration.ofDays(7);
    public static final String EXPIRED_REASON =
            "İade süresi doldu. Ödeme tarihinden itibaren ilk 7 gün içinde iade talebi oluşturabilirsiniz.";

    private RefundPolicy() {}

    public static Decision evaluate(Instant purchaseAt, Instant now, BigDecimal remainingRefundable) {
        if (purchaseAt == null) {
            return new Decision(false, null, "Satın alma zamanı doğrulanamadı.");
        }
        Instant deadline = purchaseAt.plus(WINDOW);
        if (remainingRefundable == null || remainingRefundable.signum() <= 0) {
            return new Decision(false, deadline, "Ödeme tamamen iade edildi.");
        }
        // Half-open window: [successful payment timestamp, successful payment timestamp + 7 days).
        // At the exact deadline the seven-day window has expired.
        if (!now.isBefore(deadline)) {
            return new Decision(false, deadline, EXPIRED_REASON);
        }
        return new Decision(true, deadline, null);
    }

    public record Decision(boolean eligible, Instant deadline, String ineligibleReason) {}
}
