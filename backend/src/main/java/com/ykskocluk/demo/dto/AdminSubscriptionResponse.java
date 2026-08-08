package com.ykskocluk.demo.dto;

import java.time.Instant;

/**
 * Detailed subscription DTO for admin dashboard (Phase 9).
 */
public record AdminSubscriptionResponse(
        Long id,
        Long studentId,
        String studentEmail,
        String studentFullName,
        Long coachProfileId,
        String coachFullName,
        Long packageId,
        String packageName,
        String status,
        Instant startAt,
        Instant endAt,
        boolean autoRenew,
        Instant cancelledAt,
        String terminationReason,
        Instant createdAt
) {}
