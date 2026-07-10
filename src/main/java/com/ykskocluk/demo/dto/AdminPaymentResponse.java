package com.ykskocluk.demo.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Detailed payment DTO for admin dashboard (Phase 9).
 */
public record AdminPaymentResponse(
        Long id,
        Long subscriptionId,
        String studentEmail,
        String studentFullName,
        String coachFullName,
        String packageName,
        String type,
        BigDecimal amount,
        String status,
        String providerReference,
        Instant createdAt,
        BigDecimal refundedAmount,
        BigDecimal remainingRefundableAmount
) {}
