package com.ykskocluk.demo.enums;

/**
 * Payment row type. {@code CHARGE} is a subscription charge (Phase 8). {@code REFUND} is a
 * later, separate row linked to the original via {@code source_payment_id} — the table supports
 * it now, but refund execution is out of scope in Stage 1.
 */
public enum PaymentType {
    CHARGE,
    REFUND
}
