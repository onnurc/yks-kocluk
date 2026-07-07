package com.ykskocluk.demo.integration;

/**
 * Result of an {@link IyzicoClient#refund} call.
 */
public record RefundResult(
        boolean success,
        String providerReference,
        String errorCode,
        String errorMessage
) {
}
