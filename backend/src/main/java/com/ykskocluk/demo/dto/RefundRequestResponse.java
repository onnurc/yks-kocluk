package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.RefundRequestStatus;
import com.ykskocluk.demo.enums.RefundWindow;
import java.math.BigDecimal;
import java.time.Instant;

public record RefundRequestResponse(
        Long id, RefundRequestStatus status, RefundWindow refundWindow,
        Instant requestedAt, Instant purchaseAt, long elapsedSeconds,
        Long studentId, String studentName, Long coachId, String coachName,
        Long packageId, String packageName, Long subscriptionId, Long originalPaymentId,
        BigDecimal amount, BigDecimal refundedAmount, String currency, boolean serviceStarted, int paidSessionsCount,
        int completedSessionsCount, Instant earliestPaidSessionAt, String latestRelevantSessionStatus,
        Instant adminDecisionAt, String rejectionReason, Long refundPaymentId) {
}
