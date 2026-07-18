package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.Size;

/**
 * DTO for terminating a subscription by admin.
 */
public record AdminSubscriptionTerminateRequest(
        @Size(max = 2000, message = "Termination reason cannot exceed 2000 characters")
        String reason
) {}
