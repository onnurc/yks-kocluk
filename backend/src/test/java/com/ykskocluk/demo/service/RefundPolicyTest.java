package com.ykskocluk.demo.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class RefundPolicyTest {
    private static final Instant PURCHASE = Instant.parse("2026-08-01T10:00:00Z");

    @Test void beforeSevenDays_isEligible() {
        assertThat(RefundPolicy.evaluate(PURCHASE, PURCHASE.plusSeconds(6 * 86400), BigDecimal.TEN).eligible()).isTrue();
    }

    @Test void exactlySevenDays_isEligible() {
        assertThat(RefundPolicy.evaluate(PURCHASE, PURCHASE.plus(RefundPolicy.WINDOW), BigDecimal.TEN).eligible()).isTrue();
    }

    @Test void afterSevenDays_isExpiredEvenWithRemainingAmount() {
        var decision = RefundPolicy.evaluate(PURCHASE, PURCHASE.plus(RefundPolicy.WINDOW).plusNanos(1), BigDecimal.TEN);
        assertThat(decision.eligible()).isFalse();
        assertThat(decision.ineligibleReason()).isEqualTo(RefundPolicy.EXPIRED_REASON);
    }

    @Test void fullyRefundedPayment_isNotEligible() {
        assertThat(RefundPolicy.evaluate(PURCHASE, PURCHASE.plusSeconds(1), BigDecimal.ZERO).eligible()).isFalse();
    }
}
