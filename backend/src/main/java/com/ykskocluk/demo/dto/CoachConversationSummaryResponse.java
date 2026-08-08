package com.ykskocluk.demo.dto;

import java.time.Instant;

public record CoachConversationSummaryResponse(Long conversationId, Long studentId, String studentDisplayName,
                                               String lastMessagePreview, Instant lastMessageAt,
                                               long unreadCount, boolean currentAccess) {}
