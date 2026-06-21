package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** History, newest first (sort is fixed — not user-supplied). */
    Page<Message> findByConversationIdOrderByCreatedAtDesc(Long conversationId, Pageable pageable);

    /**
     * Admin oversight message page (Phase 5c). Ordering comes from the caller's whitelisted
     * {@code Pageable} ({@code createdAt} only; defaulted DESC to match the participant view).
     */
    Page<Message> findByConversationId(Long conversationId, Pageable pageable);

    /**
     * Per-page message counts for the admin list — ONE aggregate for all conversation ids on
     * the page. Per-row COUNT is forbidden (it also yields correct numbers, so it is the trap).
     */
    @Query("""
            select m.conversation.id as conversationId, count(m) as messageCount
              from Message m
             where m.conversation.id in :ids
             group by m.conversation.id
            """)
    List<ConversationMessageCount> countByConversationIdIn(@Param("ids") Collection<Long> ids);

    /** Unread messages addressed to the reader (i.e. NOT sent by them). */
    long countByConversationIdAndSenderIdNotAndReadAtIsNull(Long conversationId, Long readerId);

    /**
     * Marks the OTHER party's unread messages as read. The {@code sender.id <> :readerId}
     * clause guarantees a reader can never flip the read receipt on their own messages.
     */
    @Modifying
    @Query("""
            update Message m set m.readAt = :now
             where m.conversation.id = :conversationId
               and m.sender.id <> :readerId
               and m.readAt is null
            """)
    int markRead(@Param("conversationId") Long conversationId,
                 @Param("readerId") Long readerId,
                 @Param("now") Instant now);
}
