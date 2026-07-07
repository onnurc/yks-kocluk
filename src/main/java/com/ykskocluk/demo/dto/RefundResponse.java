package com.ykskocluk.demo.dto;

import java.math.BigDecimal;

/**
 * Response returned after a successful admin refund request.
 */
public record RefundResponse(
        Long originalPaymentId,
        Long refundPaymentId,
        String refundStatus,
        BigDecimal amount,
        BigDecimal remainingRefundableAmount,
        String message
) {
}
