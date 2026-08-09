package com.ykskocluk.demo.dto;

import java.time.Instant;

public record AdminConversationStudentResponse(Long studentId, String displayName,
                                                Long conversationId, Instant lastMessageAt,
                                                ConversationObserverResponse observer) {
}
