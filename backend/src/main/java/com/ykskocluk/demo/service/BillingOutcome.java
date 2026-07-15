package com.ykskocluk.demo.service;

/**
 * Result of {@link SubscriptionBillingService#processDue}. Drives which email the scheduled job
 * sends (Phase 8d) and what the tests assert.
 */
public enum BillingOutcome {
    /** Charge succeeded; subscription renewed (end_at advanced, ACTIVE, failures reset). */
    CHARGED_SUCCESS,
    /** Charge failed; still within the retry window → PAST_DUE (access stays open). */
    CHARGED_FAILED_PAST_DUE,
    /** Charge failed and retries are now exhausted → EXPIRED (access cut, capacity freed). */
    CHARGED_FAILED_EXPIRED,
    /** Due with auto-renew off (cancelled) → EXPIRED without any charge. */
    EXPIRED_NO_RENEW,
    /** Another run already reserved/processed this attempt window (idempotency guard). */
    SKIPPED_ALREADY_PROCESSED,
    /** Subscription is not due / not in a chargeable state. */
    SKIPPED_NOT_DUE
}
