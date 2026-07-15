package com.ykskocluk.demo.dto;

public record StudentDashboardResponse(
        UserResponse user,
        DashboardSubscription subscription,
        DashboardPayment payment
) {
}
