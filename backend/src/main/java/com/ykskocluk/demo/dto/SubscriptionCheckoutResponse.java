package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.PaymentStatus;
import com.ykskocluk.demo.enums.SubscriptionStatus;

import java.math.BigDecimal;

public record SubscriptionCheckoutResponse(
        Long subscriptionId,
        Long paymentId,
        SubscriptionStatus subscriptionStatus,
        PaymentStatus paymentStatus,
        BigDecimal amount,
        String checkoutToken,
        String checkoutUrl
) {
}