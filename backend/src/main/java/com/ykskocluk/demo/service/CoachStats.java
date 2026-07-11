package com.ykskocluk.demo.service;

/**
 * Derived, runtime-computed discovery stats for a coach (never stored).
 * {@code rating} is null when there are no reviews yet.
 */
public record CoachStats(Double rating, int totalSessions) {

    public static CoachStats empty() {
        return new CoachStats(null, 0);
    }
}
