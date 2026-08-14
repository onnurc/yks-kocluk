package com.ykskocluk.demo.dto;

import java.math.BigDecimal;

/** Public marketing projection for active coaching packages. */
public record PublicPackageResponse(
        Long id,
        String name,
        int weeklySessions,
        int durationDays,
        BigDecimal price
) {
}
