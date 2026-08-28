package com.ykskocluk.demo.integration;

import com.ykskocluk.demo.exception.PaymentProviderException;

/**
 * Outcome of an {@link IyzicoClient#charge} call: whether the charge succeeded and the provider's
 * payment reference (for reconciliation; null/blank on failure).
 */
public record ChargeResult(boolean success, String providerReference) {
    public ChargeResult {
        if (success && (providerReference == null || providerReference.isBlank())) {
            throw new PaymentProviderException("Iyzico returned a successful charge without a reference");
        }
    }
}
