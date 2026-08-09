package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.dto.SubscriptionEmailView;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

import java.time.Instant;
import java.util.List;
import java.util.Collection;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    @EntityGraph(attributePaths = {"coachProfile.user", "pkg"})
    Optional<Subscription> findFirstByStudentIdOrderByCreatedAtDesc(Long studentId);

    boolean existsByStudentIdAndCoachProfileIdAndStatus(Long studentId, Long coachProfileId, SubscriptionStatus status);

    /** The active subscription that authorizes booking. */
    Optional<Subscription> findByStudentIdAndCoachProfileIdAndStatus(
            Long studentId, Long coachProfileId, SubscriptionStatus status);

    /**
     * Booking gate (Phase 8): a subscription that grants access — ACTIVE or PAST_DUE (grace window).
     * The live-subscription unique index guarantees at most one live sub per (student, coach), so Optional is safe.
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

    @Query("""
            select count(s) > 0 from Subscription s
             where s.student.id = :studentId
               and s.coachProfile.id = :coachProfileId
               and s.status in (com.ykskocluk.demo.enums.SubscriptionStatus.ACTIVE,
                                com.ykskocluk.demo.enums.SubscriptionStatus.PAST_DUE)
            """)
    boolean existsLiveSubscription(@Param("studentId") Long studentId,
                                   @Param("coachProfileId") Long coachProfileId);

    @Query("""
            select distinct s.student.id from Subscription s
             where s.coachProfile.id = :coachProfileId
               and s.student.id in :studentIds
               and s.status in (com.ykskocluk.demo.enums.SubscriptionStatus.ACTIVE,
                                com.ykskocluk.demo.enums.SubscriptionStatus.PAST_DUE)
            """)
    List<Long> findLiveStudentIds(@Param("coachProfileId") Long coachProfileId,
                                  @Param("studentIds") Collection<Long> studentIds);

    /**
     * History/read/subscription membership access gate (Phase 5 messaging):
     * true if the student has any valid paid/historical subscription with this coach (ACTIVE, PAST_DUE, EXPIRED, CANCELLED).
     * Enforced server-side.
     */
    @Query("""
            select count(s) > 0 from Subscription s
             where s.student.id = :studentId
               and s.coachProfile.id = :coachProfileId
               and s.status in (com.ykskocluk.demo.enums.SubscriptionStatus.ACTIVE,
                                com.ykskocluk.demo.enums.SubscriptionStatus.PAST_DUE,
                                com.ykskocluk.demo.enums.SubscriptionStatus.EXPIRED,
                                com.ykskocluk.demo.enums.SubscriptionStatus.CANCELLED)
            """)
    boolean existsHistoryAccessSubscription(@Param("studentId") Long studentId,
                                            @Param("coachProfileId") Long coachProfileId);

    /**
     * Due set for the renewal job (Phase 8c): ACTIVE subs past end_at (renewal due) plus all
     * PAST_DUE subs (retry, or expiry when auto-renew is off). EXPIRED/CANCELLED are terminal and
     * never selected — so a retry-exhausted sub (already EXPIRED at its 3rd failure) is not re-attempted.
     */
    @Query("""
            select s.id from Subscription s
             where (s.status = com.ykskocluk.demo.enums.SubscriptionStatus.ACTIVE and s.endAt <= :now)
                or  s.status = com.ykskocluk.demo.enums.SubscriptionStatus.PAST_DUE
            """)
    List<Long> findDueSubscriptionIds(@Param("now") Instant now);

    /**
     * Stuck-checkout set for cleanup (Phase 8): subscriptions left PENDING_PAYMENT since before
     * {@code cutoff} — either the payment webhook never arrived or it arrived as FAILURE. These
     * are expired so the student is no longer blocked (ALREADY_SUBSCRIBED) from re-subscribing.
     */
    @Query("""
            select s.id from Subscription s
             where s.status = com.ykskocluk.demo.enums.SubscriptionStatus.PENDING_PAYMENT
               and s.createdAt <= :cutoff
            """)
    List<Long> findStalePendingCheckoutIds(@Param("cutoff") Instant cutoff);

    /** Email snapshot for the renewal job's after-commit mail dispatch (no lazy entity access). */
    @Query("""
            select new com.ykskocluk.demo.dto.SubscriptionEmailView(
                     s.student.email, s.coachProfile.user.fullName, s.endAt, s.pkg.price, s.failedChargeCount)
              from Subscription s where s.id = :id
            """)
    SubscriptionEmailView findEmailViewById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"student", "coachProfile.user", "pkg"})
    @Query("""
            select s from Subscription s
             where s.coachProfile.id = :coachId
               and s.status in :statuses
               and s.createdAt = (select max(s2.createdAt) from Subscription s2
                    where s2.coachProfile.id = :coachId and s2.student.id = s.student.id
                      and s2.status in :statuses)
            """)
    Page<Subscription> findLatestStudents(@Param("coachId") Long coachId,
                                          @Param("statuses") Collection<SubscriptionStatus> statuses,
                                          Pageable pageable);

    @EntityGraph(attributePaths = {"student", "pkg"})
    @Query("""
            select s from Subscription s
             where s.coachProfile.id = :coachId and s.status in :statuses
            """)
    List<Subscription> findByCoachAndStatuses(@Param("coachId") Long coachId,
                                              @Param("statuses") Collection<SubscriptionStatus> statuses);

    @Query("""
            select count(distinct s.student.id) from Subscription s
             where s.coachProfile.id = :coachId and s.status in :statuses
            """)
    long countDistinctStudents(@Param("coachId") Long coachId,
                               @Param("statuses") Collection<SubscriptionStatus> statuses);

    long countByStatusIn(Collection<SubscriptionStatus> statuses);

    @EntityGraph(attributePaths = {"student", "coachProfile.user", "pkg"})
    @Query("""
            select s from Subscription s
             where (:status is null or s.status = :status)
               and (:studentId is null or s.student.id = :studentId)
               and (:coachId is null or s.coachProfile.id = :coachId)
               and (:packageId is null or s.pkg.id = :packageId)
               and (:from is null or s.createdAt >= :from)
               and (:to is null or s.createdAt < :to)
            """)
    Page<Subscription> searchAdmin(@Param("status") SubscriptionStatus status,
                                   @Param("studentId") Long studentId,
                                   @Param("coachId") Long coachId,
                                   @Param("packageId") Long packageId,
                                   @Param("from") Instant from,
                                   @Param("to") Instant to,
                                   Pageable pageable);
}
