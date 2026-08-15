package com.ykskocluk.demo.dto;

import java.time.Instant;

/**
 * Flat projection of the session fields required to dispatch an upcoming-session reminder.
 * Keeps the reminder flow independent of lazy entity associations outside the query transaction.
 */
public record SessionReminderView(
        Long sessionId,
        String studentEmail,
        String coachName,
        Instant startTime,
        String meetLink
) {
}
