package com.ykskocluk.demo.enums;

/**
 * Subscription lifecycle (Phase 8 auto-renew). {@code PENDING_PAYMENT} = created but not yet paid;
 * {@code ACTIVE} = paid &amp; live; {@code PAST_DUE} = a renewal charge failed and the sub is in the
 * retry grace window (access stays open — the booking gate accepts ACTIVE + PAST_DUE); {@code EXPIRED}
 * = retries exhausted, a cancelled sub reached end_at (no renewal), or an unpaid checkout timed out.
 */
public enum SubscriptionStatus {
    PENDING_PAYMENT,
    ACTIVE,
    PAST_DUE,
    EXPIRED,

    /**
     * Legacy terminal state from Phase 4, retained for backward compatibility.
     *
     * <p><strong>Never produced by current code</strong> — no service, migration, or seed writes
     * it (the Phase 8 cancel flow instead sets {@code auto_renew=false} and lets the sub run to
     * end_at, then EXPIREs). It is kept only so that (a) any historical {@code 'CANCELLED'} row
     * still deserializes under {@code @Enumerated(EnumType.STRING)}, and (b) the defensive
     * terminal-state branches in {@code SubscriptionBillingService.decideBranch} and
     * {@code SubscriptionService.terminateSubscription} keep handling it.
     *
     * @deprecated do not assign to new subscriptions; use {@link #EXPIRED} for terminal
     *             non-renewing states. Remove only once the production database is confirmed to
     *             hold no {@code CANCELLED} rows.
     */
    @Deprecated
    CANCELLED,

    TERMINATED
}
