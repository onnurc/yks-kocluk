package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body for suspending a user.
 */
public record SuspendRequest(
        @Size(max = 2000, message = "Suspension reason cannot exceed 2000 characters")
        String reason
) {
}
