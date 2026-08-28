package com.ykskocluk.demo.integration;

import com.ykskocluk.demo.exception.PaymentProviderException;

/**
 * Result of an {@link IyzicoClient#refund} call.
 */
public record RefundResult(
        boolean success,
        String providerReference,
        String errorCode,
        String errorMessage
) {
    public RefundResult {
        if (success && (providerReference == null || providerReference.isBlank())) {
            throw new PaymentProviderException("Iyzico returned a successful refund without a reference");
        }
    }
}
