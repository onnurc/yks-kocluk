package com.ykskocluk.demo.dto;

import java.time.Instant;

/**
 * Detailed subscription DTO for admin dashboard (Phase 9).
 */
public record AdminSubscriptionResponse(
        Long id,
        String studentEmail,
        String studentFullName,
        String coachFullName,
        String packageName,
        String status,
        Instant startAt,
        Instant endAt,
        boolean autoRenew,
        Instant cancelledAt,
        String terminationReason,
        Instant createdAt
) {}
