package com.ykskocluk.demo.dto;

import java.time.Instant;

/**
 * Admin oversight summary of a conversation (Phase 5c). Read-only projection for the
 * {@code GET /api/v1/admin/conversations} list — participants + activity, no message bodies.
 * {@code messageCount} is computed by a single per-page aggregate query, never per row.
 */
public record ConversationSummaryResponse(
        Long conversationId,
        StudentRef student,
        CoachRef coach,
        Instant lastMessageAt,
        long messageCount,
        ConversationObserverResponse observer
) {
    public record StudentRef(Long id, String fullName) {
    }

    public record CoachRef(Long id, String fullName, String universityName) {
    }
}
