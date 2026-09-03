package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminCampaignRequest(
        boolean enabled,
        @NotBlank @Size(max = 120) String title,
        @Size(max = 500) String description,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotNull DiscountType discountType,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 10, fraction = 2) BigDecimal discountValue
) {}
