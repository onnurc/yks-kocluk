package com.ykskocluk.demo.enums;

/**
 * Subscription lifecycle. PENDING_PAYMENT is added in Phase 8 (payment integration);
 * Phase 4 creates ACTIVE subscriptions directly.
 */
public enum SubscriptionStatus {
    ACTIVE,
    EXPIRED,
    CANCELLED
}
