package com.ykskocluk.demo.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Detailed payment DTO for admin dashboard (Phase 9).
 */
public record AdminPaymentResponse(
        Long id,
        Long subscriptionId,
        Long sourcePaymentId,
        Long studentId,
        String studentEmail,
        String studentFullName,
        Long coachProfileId,
        String coachFullName,
        Long packageId,
        String packageName,
        String type,
        BigDecimal amount,
        String status,
        String providerReference,
        Instant createdAt,
        Instant succeededAt,
        BigDecimal refundedAmount,
        BigDecimal remainingRefundableAmount,
        boolean refundEligible,
        Instant refundDeadline,
        String refundIneligibleReason
    ) {}
