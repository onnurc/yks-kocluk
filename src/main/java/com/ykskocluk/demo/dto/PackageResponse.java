package com.ykskocluk.demo.dto;

import java.math.BigDecimal;

public record PackageResponse(
        Long id,
        String name,
        int weeklySessions,
        int durationDays,
        BigDecimal price
) {
}
