package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record DashboardPayment(
        Long id,
        PaymentStatus status,
        BigDecimal amount,
        Instant createdAt
) {
}
