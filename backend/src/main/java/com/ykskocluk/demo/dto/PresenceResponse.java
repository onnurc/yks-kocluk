package com.ykskocluk.demo.dto;

/** Conversation-scoped participant presence pushed over STOMP. */
public record PresenceResponse(Long userId, boolean online) {
}
