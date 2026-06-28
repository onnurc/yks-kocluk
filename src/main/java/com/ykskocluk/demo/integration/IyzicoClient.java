package com.ykskocluk.demo.integration;

import java.math.BigDecimal;

/**
 * Payment provider (iyzico) — one of the four sanctioned external-service interfaces (stub-first).
 * Stage 1 ships only {@link StubIyzicoClient} (always succeeds, no real money, no company). The
 * real saved-card recurring charge lands in Stage 2 (sandbox), then production.
 */
public interface IyzicoClient {

    /**
     * Charges a saved card for a recurring subscription renewal. {@code idempotencyKey} makes the
     * same logical charge safe to retry — the provider (and our own {@code UNIQUE} key) must not
     * double-charge for the same key.
     */
    ChargeResult charge(String savedCardToken, BigDecimal amount, String idempotencyKey);
}
