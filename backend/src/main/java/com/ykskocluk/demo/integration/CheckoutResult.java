package com.ykskocluk.demo.integration;

import com.ykskocluk.demo.exception.PaymentProviderException;

public record CheckoutResult(
        String checkoutToken,
        String checkoutUrl
) {
    public CheckoutResult {
        if (checkoutToken == null || checkoutToken.isBlank()
                || checkoutUrl == null || checkoutUrl.isBlank()) {
            throw new PaymentProviderException("Iyzico returned incomplete checkout data");
        }
    }
}
