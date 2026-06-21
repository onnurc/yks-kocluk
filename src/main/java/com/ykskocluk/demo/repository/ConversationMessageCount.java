package com.ykskocluk.demo.repository;

/**
 * Interface projection for the per-page message-count aggregate
 * ({@code count(*) ... group by conversation_id}). Lets the admin list compute
 * {@code messageCount} for a whole page of conversations in ONE query (no per-row COUNT).
 */
public interface ConversationMessageCount {

    Long getConversationId();

    long getMessageCount();
}
