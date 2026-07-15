package com.ykskocluk.demo.service;

import java.time.Instant;

/**
 * Published inside the booking transaction and consumed AFTER_COMMIT to trigger external
 * side effects (Meet link + email). Carries a snapshot of the fields the listener needs so
 * it never touches detached/lazy entities after the transaction closes.
 */
public record SessionBookedEvent(
        Long sessionId,
        String studentEmail,
        String coachName,
        Instant startTime,
        Instant endTime
) {
}
