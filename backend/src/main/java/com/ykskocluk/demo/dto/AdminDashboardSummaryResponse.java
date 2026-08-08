package com.ykskocluk.demo.dto;

import java.math.BigDecimal;

public record AdminDashboardSummaryResponse(
        long totalStudentCount,
        long activeCoachCount,
        long pendingCoachApprovalCount,
        long activeSubscriptionCount,
        long salesThisMonthCount,
        BigDecimal grossRevenueThisMonth,
        BigDecimal refundAmountThisMonth,
        long openReportCount,
        long scheduledSessionCount,
        long completedSessionCountThisMonth
) {}
