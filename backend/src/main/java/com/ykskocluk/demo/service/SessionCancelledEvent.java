package com.ykskocluk.demo.service;

import java.time.Instant;

/**
 * Published inside the cancellation transaction and consumed AFTER_COMMIT (mirrors
 * {@link SessionBookedEvent}). {@code late} distinguishes a &lt;24h cancel (slot stays booked,
 * quota burned) from an early one (slot reopened, quota returned) — the mail copy differs.
 */
public record SessionCancelledEvent(
        Long sessionId,
        String studentEmail,
        String studentName,
        String coachEmail,
        String coachName,
        Instant startTime,
        boolean late
) {
}
