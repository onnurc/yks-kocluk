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
import java.util.Optional;

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

    @Query("""
            select count(m) from Message m
             where m.conversation.coachProfile.user.id = :coachUserId
               and m.sender.id <> :coachUserId and m.readAt is null
            """)
    long countUnreadForCoach(@Param("coachUserId") Long coachUserId);

    /**
     * Total unread across every conversation the user can actually open — the authoritative
     * number behind the nav badge, pushed to {@code /user/queue/notifications} so the client
     * never has to increment (and drift) a count of its own across tabs and reconnects.
     *
     * <p>The student branch repeats {@code SubscriptionRepository.existsHistoryAccessSubscription}
     * as an EXISTS rather than counting every row: {@code MessageService.myConversations} hides
     * conversations the student has no history access to, so counting them here would show a badge
     * for a thread that isn't in the list. Coaches have no such gate — their side is unconditional.
     * Deliberately ONE aggregate; the per-conversation counts stay in
     * {@link #countUnreadByConversationIds}.
     */
    @Query("""
            select count(m) from Message m
             where m.sender.id <> :userId
               and m.readAt is null
               and (m.conversation.coachProfile.user.id = :userId
                    or (m.conversation.student.id = :userId
                        and exists (select s.id from Subscription s
                                     where s.student.id = :userId
                                       and s.coachProfile.id = m.conversation.coachProfile.id
                                       and s.status in (
                                            com.ykskocluk.demo.enums.SubscriptionStatus.ACTIVE,
                                            com.ykskocluk.demo.enums.SubscriptionStatus.PAST_DUE,
                                            com.ykskocluk.demo.enums.SubscriptionStatus.EXPIRED,
                                            com.ykskocluk.demo.enums.SubscriptionStatus.CANCELLED))))
            """)
    long countUnreadForUser(@Param("userId") Long userId);

    Optional<Message> findFirstByConversationIdOrderByCreatedAtDesc(Long conversationId);

    @Query("""
            select m from Message m
             where m.conversation.id in :conversationIds
               and m.createdAt = (select max(m2.createdAt) from Message m2
                    where m2.conversation.id = m.conversation.id)
             order by m.conversation.id, m.id desc
            """)
    List<Message> findLatestByConversationIds(@Param("conversationIds") Collection<Long> conversationIds);

    @Query("""
            select m.conversation.id as conversationId, count(m) as unreadCount
              from Message m
             where m.conversation.id in :conversationIds
               and m.sender.id <> :readerId
               and m.readAt is null
             group by m.conversation.id
            """)
    List<ConversationUnreadCount> countUnreadByConversationIds(
            @Param("conversationIds") Collection<Long> conversationIds,
            @Param("readerId") Long readerId);

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
