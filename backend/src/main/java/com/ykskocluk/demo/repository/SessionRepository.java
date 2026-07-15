package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.Session;
import com.ykskocluk.demo.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {

    List<Session> findByStudentIdOrderByStartTimeDesc(Long studentId);

    List<Session> findByCoachProfileIdOrderByStartTimeDesc(Long coachProfileId);

    Optional<Session> findByIdAndStudentId(Long id, Long studentId);

    Optional<Session> findByIdAndCoachProfileId(Long id, Long coachProfileId);

    /**
     * Per-coach count of sessions in a given status, batched for the discovery-stats seam
     * ({@code CoachStatsService}). Coaches with zero are simply absent from the result.
     */
    @Query("""
            select new com.ykskocluk.demo.repository.CoachSessionCount(s.coachProfile.id, count(s))
              from Session s
             where s.coachProfile.id in :coachProfileIds and s.status = :status
             group by s.coachProfile.id
            """)
    List<CoachSessionCount> countByCoachAndStatus(@Param("coachProfileIds") Collection<Long> coachProfileIds,
                                                  @Param("status") SessionStatus status);

    /**
     * Weekly-quota count: quota-consuming sessions under a subscription whose start falls
     * in the [weekStart, weekEnd) window. {@code statuses} is passed in so Phase 4d can add
     * LATE_CANCELLED/NO_SHOW without changing this query.
     */
    @Query("""
            select count(s) from Session s
             where s.subscription.id = :subscriptionId
               and s.status in :statuses
               and s.startTime >= :weekStart and s.startTime < :weekEnd
            """)
    long countQuotaConsuming(@Param("subscriptionId") Long subscriptionId,
                             @Param("statuses") Collection<SessionStatus> statuses,
                             @Param("weekStart") Instant weekStart,
                             @Param("weekEnd") Instant weekEnd);
}
