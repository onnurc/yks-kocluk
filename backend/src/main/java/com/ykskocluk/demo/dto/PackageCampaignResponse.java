package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;

public record PackageCampaignResponse(
        boolean enabled,
        boolean currentlyActive,
        String title,
        String description,
        Instant startsAt,
        Instant endsAt,
        DiscountType discountType,
        BigDecimal discountValue
) {}
