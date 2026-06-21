package com.ykskocluk.demo.dto;

import java.time.Instant;

public record MessageResponse(
        Long id,
        Long conversationId,
        Long senderId,
        String senderName,
        String content,
        Instant sentAt,
        Instant readAt
) {
}
