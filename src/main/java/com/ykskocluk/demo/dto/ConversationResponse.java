package com.ykskocluk.demo.dto;

import java.time.Instant;

public record ConversationResponse(
        Long id,
        Long coachProfileId,
        String coachName,
        String studentName,
        Instant lastMessageAt,
        long unreadCount
) {
}
