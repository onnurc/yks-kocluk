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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    long countByCoachProfileIdAndStatusAndStartTimeGreaterThanEqualAndStartTimeLessThan(
            Long coachId, SessionStatus status, Instant from, Instant to);

    long countByCoachProfileIdAndStatusAndStartTimeAfter(Long coachId, SessionStatus status, Instant now);

    Optional<Session> findFirstByCoachProfileIdAndStatusAndStartTimeAfterOrderByStartTimeAsc(
            Long coachId, SessionStatus status, Instant now);

    Optional<Session> findFirstByCoachProfileIdAndStudentIdAndStatusAndStartTimeAfterOrderByStartTimeAsc(
            Long coachId, Long studentId, SessionStatus status, Instant now);

    @Query("""
            select count(s) > 0 from Session s
             where s.coachProfile.id = :coachId
               and s.status in :statuses
               and s.startTime < :endTime and s.endTime > :startTime
            """)
    boolean existsOverlap(@Param("coachId") Long coachId,
                          @Param("statuses") Collection<SessionStatus> statuses,
                          @Param("startTime") Instant startTime,
                          @Param("endTime") Instant endTime);

    @Query("""
            select s from Session s
             where s.coachProfile.id = :coachId
               and (:from is null or s.startTime >= :from)
               and (:to is null or s.startTime < :to)
               and (:status is null or s.status = :status)
               and (:studentId is null or s.student.id = :studentId)
            """)
    Page<Session> findCoachCalendar(@Param("coachId") Long coachId,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to,
                                    @Param("status") SessionStatus status,
                                    @Param("studentId") Long studentId,
                                    Pageable pageable);
}
