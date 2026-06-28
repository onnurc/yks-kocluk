package com.ykskocluk.demo.enums;

/**
 * Charge attempt outcome. {@code PENDING} is reserved <em>before</em> the external charge call
 * so the {@code UNIQUE(idempotency_key)} guard prevents a double external charge (Stage 2 money
 * safety); the row is then finalized to {@code SUCCESS} or {@code FAILED} after the call returns.
 */
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED
}
