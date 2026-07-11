package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.SubscriptionStatus;
import java.time.Instant;

public record DashboardSubscription(
        Long id,
        SubscriptionStatus status,
        Long coachId,
        String coachName,
        Long packageId,
        String packageName,
        Instant startAt,
        Instant endAt,
        boolean autoRenew,
        Instant cancelledAt,
        String terminationReason
) {
}
