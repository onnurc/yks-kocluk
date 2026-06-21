package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.SubscriptionStatus;

import java.time.Instant;

public record SubscriptionResponse(
        Long id,
        Long coachProfileId,
        String coachName,
        Long packageId,
        String packageName,
        int weeklySessions,
        SubscriptionStatus status,
        Instant startAt,
        Instant endAt
) {
}
