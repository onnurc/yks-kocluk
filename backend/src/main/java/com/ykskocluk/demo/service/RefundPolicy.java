package com.ykskocluk.demo.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/** Authoritative product-level refund window for successful plan charges. */
public final class RefundPolicy {
    public static final Duration WINDOW = Duration.ofDays(7);
    public static final String EXPIRED_REASON = "Satın alma tarihinden itibaren 7 günlük iade süresi doldu.";

    private RefundPolicy() {}

    public static Decision evaluate(Instant purchaseAt, Instant now, BigDecimal remainingRefundable) {
        if (purchaseAt == null) {
            return new Decision(false, null, "Satın alma zamanı doğrulanamadı.");
        }
        Instant deadline = purchaseAt.plus(WINDOW);
        if (remainingRefundable == null || remainingRefundable.signum() <= 0) {
            return new Decision(false, deadline, "Ödeme tamamen iade edildi.");
        }
        if (now.isAfter(deadline)) {
            return new Decision(false, deadline, EXPIRED_REASON);
        }
        return new Decision(true, deadline, null);
    }

    public record Decision(boolean eligible, Instant deadline, String ineligibleReason) {}
}
