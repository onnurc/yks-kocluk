package com.ykskocluk.demo.dto;

import java.math.BigDecimal;

public record AdminDashboardSummaryResponse(
        long totalStudentCount,
        long totalCoachCount,
        long activeCoachCount,
        long pendingCoachApplicationCount,
        long activeSubscriptionCount,
        long salesThisMonthCount,
        BigDecimal grossRevenueThisMonth,
        BigDecimal refundAmountThisMonth,
        BigDecimal netCollectedThisMonth,
        long openReportCount,
        long scheduledSessionCount,
        long completedSessionCountThisMonth
) {}
