package com.ykskocluk.demo.integration;

public record CheckoutResult(
        String checkoutToken,
        String checkoutUrl
) {
}