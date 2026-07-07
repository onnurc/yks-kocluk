package com.ykskocluk.demo.dto;

import java.time.Instant;

/**
 * Response DTO for admin subscription termination.
 */
public record AdminSubscriptionTerminateResponse(
        Long subscriptionId,
        String status,
        Instant terminatedAt,
        String message
) {}
