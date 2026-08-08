package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.SubscriptionStatus;
import java.time.Instant;

public record CoachStudentResponse(
        Long studentId,
        String displayName,
        Long packageId,
        String packageName,
        SubscriptionStatus subscriptionStatus,
        Instant subscriptionStart,
        Instant subscriptionEnd,
        long sessionsUsedInCurrentWeek,
        long sessionsRemainingInCurrentWeek,
        Long conversationId,
        SessionResponse nextSession
) {}
