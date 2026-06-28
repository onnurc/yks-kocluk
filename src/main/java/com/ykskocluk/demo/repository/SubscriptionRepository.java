package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    boolean existsByStudentIdAndCoachProfileIdAndStatus(Long studentId, Long coachProfileId, SubscriptionStatus status);

    /** The active subscription that authorizes booking. */
    Optional<Subscription> findByStudentIdAndCoachProfileIdAndStatus(
            Long studentId, Long coachProfileId, SubscriptionStatus status);

    /**
     * Booking gate (Phase 8): a subscription that grants access — ACTIVE or PAST_DUE (grace window).
     * The V10 partial-unique index guarantees at most one live sub per (student, coach), so Optional is safe.
     */
    @Query("""
            select s from Subscription s
             where s.student.id = :studentId
               and s.coachProfile.id = :coachProfileId
               and s.status in (com.ykskocluk.demo.enums.SubscriptionStatus.ACTIVE,
                                com.ykskocluk.demo.enums.SubscriptionStatus.PAST_DUE)
            """)
    Optional<Subscription> findLiveSubscription(@Param("studentId") Long studentId,
                                                @Param("coachProfileId") Long coachProfileId);

    /**
     * Message gate: true if the student has EVER subscribed to this coach (any status —
     * active or past). Never-subscribed → no messaging. Enforced server-side.
     */
    boolean existsByStudentIdAndCoachProfileId(Long studentId, Long coachProfileId);
}
