package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.RefundRequestStatus;
import com.ykskocluk.demo.enums.RefundEligibilityStatus;
import com.ykskocluk.demo.enums.PackageType;

import java.math.BigDecimal;
import java.time.Instant;

public record RefundEligibilityResponse(
        Long subscriptionId,
        boolean eligible,
        RefundEligibilityStatus status,
        BigDecimal refundableAmount,
        String currency,
        Instant deadline,
        String explanation,
        RefundRequestStatus activeRequestStatus,
        PackageType packageType,
        String policy,
        Instant cancellationRequestedAt,
        int usedMonthCount,
        Instant currentServicePeriodEnd,
        Instant accessEndsAt,
        BigDecimal consumedAmount
) { }
