package com.ykskocluk.demo.dto;

import java.time.Instant;

public record ConversationResponse(
        Long id,
        Long coachProfileId,
        String coachName,
        String studentName,
        String lastMessage,
        Instant lastMessageAt,
        long unreadCount,
        Long counterpartUserId,
        boolean counterpartOnline,
        ConversationObserverResponse observer
) {
}
