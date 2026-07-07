package com.ykskocluk.demo.dto;

import java.time.Instant;

/**
 * Response after logging admin access to a conversation.
 */
public record AdminConversationAccessResponse(
        Long id,
        Long adminUserId,
        Long conversationId,
        String reason,
        Instant accessedAt
) {
}
