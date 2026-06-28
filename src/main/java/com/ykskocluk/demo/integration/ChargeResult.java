package com.ykskocluk.demo.integration;

/**
 * Outcome of an {@link IyzicoClient#charge} call: whether the charge succeeded and the provider's
 * payment reference (for reconciliation; null/blank on failure).
 */
public record ChargeResult(boolean success, String providerReference) {
}
