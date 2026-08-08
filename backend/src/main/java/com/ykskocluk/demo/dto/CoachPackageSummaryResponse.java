package com.ykskocluk.demo.dto;

import java.math.BigDecimal;

public record CoachPackageSummaryResponse(Long packageId, String packageName,
                                          long activeStudentCount, long currentSubscriptionCount,
                                          BigDecimal grossSales) {}
