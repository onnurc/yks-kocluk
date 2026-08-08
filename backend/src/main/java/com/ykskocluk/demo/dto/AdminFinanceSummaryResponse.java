package com.ykskocluk.demo.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminFinanceSummaryResponse(Instant from, Instant to, BigDecimal grossRevenue,
                                          long successfulPaymentCount, long failedPaymentCount,
                                          long pendingPaymentCount, BigDecimal refundTotal,
                                          BigDecimal netCollectedAmount) {}
