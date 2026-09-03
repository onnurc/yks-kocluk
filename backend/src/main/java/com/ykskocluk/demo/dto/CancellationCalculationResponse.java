package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.PackageType;

import java.math.BigDecimal;
import java.time.Instant;

public record CancellationCalculationResponse(
        Instant cancellationRequestedAt,
        int usedMonths,
        Instant accessEndsAt,
        BigDecimal refundableAmount,
        BigDecimal consumedAmount,
        String policy,
        PackageType packageType
) {}
