package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
