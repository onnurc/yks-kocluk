package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.Conversation;
import com.ykskocluk.demo.dto.AdminConversationCoachResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByStudentIdAndCoachProfileId(Long studentId, Long coachProfileId);

    /** Inbox for a user (either side), newest activity first. */
    @Query("""
            select c from Conversation c
             where c.student.id = :userId or c.coachProfile.user.id = :userId
             order by c.lastMessageAt desc
            """)
    List<Conversation> findForUser(@Param("userId") Long userId);

    /** Conversation-scoped presence fan-out targets for one authenticated participant. */
    @Query("""
            select c.id from Conversation c
             where c.student.id = :userId or c.coachProfile.user.id = :userId
            """)
    List<Long> findIdsForUser(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"student"})
    List<Conversation> findByCoachProfileIdOrderByLastMessageAtDesc(Long coachProfileId);

    /**
     * Admin oversight list (Phase 5c) — every conversation, independent of subscription/user
     * status (admin sees everything, incl. soft-deleted participants). The entity graph fetches
     * the participant names in the SAME query (all paths are single-valued → one join, no N+1,
     * SQL-side pagination). Sort/page come from the caller's whitelisted {@code Pageable}.
     */
    @EntityGraph(attributePaths = {"student", "coachProfile.user", "coachProfile.university"})
    @Query("select c from Conversation c")
    Page<Conversation> findAllForAdmin(Pageable pageable);

    @Query(value = """
            select new com.ykskocluk.demo.dto.AdminConversationCoachResponse(
                c.coachProfile.id, c.coachProfile.user.fullName, null, count(c))
              from Conversation c
             group by c.coachProfile.id, c.coachProfile.user.fullName
            """,
            countQuery = "select count(distinct c.coachProfile.id) from Conversation c")
    Page<AdminConversationCoachResponse> findCoachDirectory(Pageable pageable);

    @EntityGraph(attributePaths = {"student"})
    Page<Conversation> findByCoachProfileId(Long coachProfileId, Pageable pageable);

    /**
     * Claims the right to email the STUDENT about a new message in this conversation. Mirrors
     * {@code SessionRepository.claimReminder}: an atomic conditional UPDATE whose 0-rows result
     * means "already notified inside the debounce window" (or a concurrent send won the race), so
     * the caller simply skips. {@code threshold} = now - debounce window; a null column means the
     * student has never been notified about this conversation and always wins the claim.
     *
     * <p>Returns rows-affected rather than a boolean so it composes with the {@code > 0} idiom
     * the reminder job already uses.
     */
    @Modifying
    @Query("""
            update Conversation c set c.studentNotifiedAt = :now
             where c.id = :id
               and (c.studentNotifiedAt is null or c.studentNotifiedAt < :threshold)
            """)
    int claimStudentNotification(@Param("id") Long id, @Param("now") Instant now,
                                 @Param("threshold") Instant threshold);

    /** Coach-side twin of {@link #claimStudentNotification}. */
    @Modifying
    @Query("""
            update Conversation c set c.coachNotifiedAt = :now
             where c.id = :id
               and (c.coachNotifiedAt is null or c.coachNotifiedAt < :threshold)
            """)
    int claimCoachNotification(@Param("id") Long id, @Param("now") Instant now,
                               @Param("threshold") Instant threshold);
}
