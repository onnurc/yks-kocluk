package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.SubscriptionResponse;

/**
 * Result of {@link SubscriptionBillingService#cancel}. {@code newlyCancelled} is false when the
 * subscription was already cancelled (idempotent no-op) — the controller fires the confirmation
 * email only when it is true. {@code studentEmail} is the snapshot the controller needs to send it.
 */
public record CancelResult(SubscriptionResponse subscription, boolean newlyCancelled, String studentEmail) {
}
