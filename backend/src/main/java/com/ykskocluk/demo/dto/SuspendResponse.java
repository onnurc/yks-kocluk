package com.ykskocluk.demo.dto;

/**
 * Response after suspending a user.
 */
public record SuspendResponse(
        Long userId,
        String status,
        String reason,
        String email
) {
}
