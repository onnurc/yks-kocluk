package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.PackageType;

import java.math.BigDecimal;
import java.util.List;

public record AdminPackageResponse(
        Long id,
        PackageType packageType,
        String name,
        BigDecimal basePrice,
        BigDecimal effectivePrice,
        boolean active,
        Integer durationMonths,
        Integer applicableMonthsRemaining,
        int evaluationMeetingsPerMonth,
        int weeklyMeetingsPerMonth,
        int totalMeetingsPerMonth,
        PackageCampaignResponse campaign,
        List<PackagePriceTierResponse> priceTiers
) {}
