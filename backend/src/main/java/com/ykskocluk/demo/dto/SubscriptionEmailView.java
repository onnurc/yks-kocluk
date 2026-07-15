package com.ykskocluk.demo.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Flat projection of the fields a renewal/expiry email needs, fetched by id <em>after</em>
 * {@code processDue} commits — a snapshot, not a detached entity, so the dispatch point (the renewal
 * job) never touches lazy associations outside a session.
 */
public record SubscriptionEmailView(
        String studentEmail,
        String coachName,
        Instant endAt,
        BigDecimal amount,
        int failedChargeCount
) {
}
