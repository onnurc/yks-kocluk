package com.ykskocluk.demo.dto;

/**
 * DTO for terminating a subscription by admin.
 */
public record AdminSubscriptionTerminateRequest(
        String reason
) {}
