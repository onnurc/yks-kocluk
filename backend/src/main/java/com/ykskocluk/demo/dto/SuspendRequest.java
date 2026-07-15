package com.ykskocluk.demo.dto;

/**
 * Request body for suspending a user.
 */
public record SuspendRequest(
        String reason
) {
}
