package com.ykskocluk.demo.service;

import java.math.BigDecimal;
import java.time.Instant;

public record PurchaseConfirmedEvent(Long paymentId, String recipientEmail, String studentName,
                                     String packageName, String coachEmail, String coachName, BigDecimal amount,
                                     String currency, Instant purchasedAt, Instant periodEndAt) {
}
