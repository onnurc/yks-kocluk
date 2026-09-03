package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.PackageType;

import java.math.BigDecimal;

/** Safe public projection: no internal campaign ids, versions, audit data, or inactive pricing internals. */
public record PackageOfferResponse(
        Long id,
        PackageType packageType,
        String name,
        boolean active,
        boolean purchasable,
        Integer durationMonths,
        Integer untilExamMonthsRemaining,
        BigDecimal listPrice,
        BigDecimal effectivePrice,
        boolean campaignActive,
        String campaignTitle,
        String campaignDescription,
        int evaluationMeetingsPerMonth,
        int weeklyMeetingsPerMonth,
        int totalMeetingsPerMonth
) {}
