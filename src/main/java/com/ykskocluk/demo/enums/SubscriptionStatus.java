package com.ykskocluk.demo.enums;

/**
 * Subscription lifecycle (Phase 8 auto-renew). {@code ACTIVE} = paid &amp; live; {@code PAST_DUE} =
 * a renewal charge failed and the sub is in the retry grace window (access stays open — the booking
 * gate accepts ACTIVE + PAST_DUE); {@code EXPIRED} = retries exhausted, or a cancelled sub reached
 * end_at (no renewal). {@code CANCELLED} is the legacy terminal state from Phase 4 — note the Phase 8
 * cancel flow sets {@code auto_renew=false} and lets the sub run to end_at then EXPIRE, rather than
 * flipping to CANCELLED.
 */
public enum SubscriptionStatus {
    ACTIVE,
    PAST_DUE,
    EXPIRED,
    CANCELLED
}
