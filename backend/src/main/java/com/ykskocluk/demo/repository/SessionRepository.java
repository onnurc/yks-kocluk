package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.dto.SessionReminderView;
import com.ykskocluk.demo.entity.Session;
import com.ykskocluk.demo.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SessionRepository extends JpaRepository<Session, Long>, JpaSpecificationExecutor<Session> {

    List<Session> findByStudentIdOrderByStartTimeDesc(Long studentId);

    List<Session> findByCoachProfileIdOrderByStartTimeDesc(Long coachProfileId);

    List<Session> findBySubscriptionIdOrderByStartTimeAsc(Long subscriptionId);

    Optional<Session> findByIdAndStudentId(Long id, Long studentId);

    Optional<Session> findByIdAndCoachProfileId(Long id, Long coachProfileId);

    /**
     * Candidate set for {@code SessionReminderJob}: PLANNED sessions starting within the lead
     * window that haven't been reminded yet. {@code startTime > :now} excludes anything already
     * underway/past (e.g. after job downtime); {@code reminderSentAt is null} excludes anything
     * already claimed by a previous run.
     *
     * <p>{@code :requireMeetLink} mirrors {@code app.meet-link.enabled}. When links are generated
     * automatically (true), a link-less session is skipped so a reminder never carries a dead
     * link — it is picked up on a later run once the after-commit creation lands. When automatic
     * generation is off (false, the default), <strong>no session ever gets a link this way</strong>,
     * so applying that filter would silently suppress every reminder; the filter is therefore
     * dropped and link-less sessions are reminded (the mail omits the join block — see
     * {@code ResendMailClient.sendSessionReminder}).
     */
    @Query("""
            select new com.ykskocluk.demo.dto.SessionReminderView(
                     s.id, s.student.email, s.coachProfile.user.fullName, s.startTime, s.meetLink)
              from Session s
             where s.status = com.ykskocluk.demo.enums.SessionStatus.PLANNED
               and s.reminderSentAt is null
               and (:requireMeetLink = false or s.meetLink is not null)
               and s.startTime > :now and s.startTime <= :horizon
            """)
    List<SessionReminderView> findReminderCandidates(@Param("now") Instant now,
                                                     @Param("horizon") Instant horizon,
                                                     @Param("requireMeetLink") boolean requireMeetLink);

    /**
     * Atomic claim (mirrors {@code CoachProfileRepository.incrementActiveStudentCountIfRoom}):
     * rows affected is 0 or 1, so a concurrent/overlapping job run can never send the same
     * reminder twice. Caller must run this in its own transaction (see
     * {@code SessionService#claimReminder}) so the claim commits before the mail is dispatched.
     */
    @Modifying
    @Query("""
            update Session s set s.reminderSentAt = :now
             where s.id = :id and s.reminderSentAt is null
            """)
    int claimReminder(@Param("id") Long id, @Param("now") Instant now);

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

    @Query("""
            select s.subscription.id as subscriptionId, count(s) as sessionCount
              from Session s
             where s.subscription.id in :subscriptionIds
               and s.status in :statuses
               and s.startTime >= :weekStart and s.startTime < :weekEnd
             group by s.subscription.id
            """)
    List<SubscriptionSessionCount> countQuotaConsumingBySubscription(
            @Param("subscriptionIds") Collection<Long> subscriptionIds,
            @Param("statuses") Collection<SessionStatus> statuses,
            @Param("weekStart") Instant weekStart,
            @Param("weekEnd") Instant weekEnd);

    @Query("""
            select s from Session s
             where s.coachProfile.id = :coachId
               and s.student.id in :studentIds
               and s.status = :status
               and s.startTime > :now
               and s.startTime = (select min(s2.startTime) from Session s2
                    where s2.coachProfile.id = :coachId
                      and s2.student.id = s.student.id
                      and s2.status = :status
                      and s2.startTime > :now)
             order by s.student.id, s.id
            """)
    List<Session> findNextForCoachStudents(@Param("coachId") Long coachId,
                                           @Param("studentIds") Collection<Long> studentIds,
                                           @Param("status") SessionStatus status,
                                           @Param("now") Instant now);

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

    long countByStatusAndStartTimeAfter(SessionStatus status, Instant now);

    long countByStatusAndStartTimeGreaterThanEqualAndStartTimeLessThan(
            SessionStatus status, Instant from, Instant to);

    /*
     * cast(:param as ...) on the bare "is null" side of each optional filter (not needed on the
     * comparison side, which already has type context from the compared column) — PostgreSQL 18
     * rejects an untyped bind parameter whose only usage is a plain "? is null" check (SQLState
     * 42P18). See the longer note on PaymentRepository#countForPeriod for the full explanation.
     */
    @Query(value = """
            select * from (
                select s.id as id, 'PAID' as type, s.coach_profile_id as "coachProfileId",
                       cp.user_id as "coachUserId", cu.full_name as "coachName",
                       s.student_user_id as "studentId", su.full_name as "studentName", su.email as "studentEmail",
                       s.start_time as "startsAt", s.end_time as "endsAt", s.status as status,
                       s.subscription_id as "subscriptionId", s.meet_link as "meetingUrl", s.created_at as "createdAt"
                  from sessions s
                  join coach_profiles cp on cp.id = s.coach_profile_id
                  join users cu on cu.id = cp.user_id
                  join users su on su.id = s.student_user_id
                 where (:type = 'ALL' or :type = 'PAID')
                union all
                select t.id as id, 'TRIAL' as type, t.coach_profile_id as "coachProfileId",
                       cp.user_id as "coachUserId", cu.full_name as "coachName",
                       t.student_user_id as "studentId", su.full_name as "studentName", su.email as "studentEmail",
                       t.start_time as "startsAt", t.end_time as "endsAt", t.status as status,
                       cast(null as bigint) as "subscriptionId", t.meeting_url as "meetingUrl", t.created_at as "createdAt"
                  from trial_consultations t
                  join coach_profiles cp on cp.id = t.coach_profile_id
                  join users cu on cu.id = cp.user_id
                  join users su on su.id = t.student_user_id
                 where (:type = 'ALL' or :type = 'TRIAL')
            ) x
            where (cast(:status as text) is null or x.status = :status)
              and (cast(:coachId as bigint) is null or x."coachProfileId" = :coachId)
              and (cast(:studentId as bigint) is null or x."studentId" = :studentId)
              and (cast(:from as timestamptz) is null or x."startsAt" >= :from)
              and (cast(:to as timestamptz) is null or x."startsAt" < :to)
            order by x."createdAt" desc, x.id desc
            """,
            countQuery = """
            select count(*) from (
                select s.id, s.coach_profile_id, s.student_user_id, s.start_time, s.status
                  from sessions s where (:type = 'ALL' or :type = 'PAID')
                union all
                select t.id, t.coach_profile_id, t.student_user_id, t.start_time, t.status
                  from trial_consultations t where (:type = 'ALL' or :type = 'TRIAL')
            ) x
            where (cast(:status as text) is null or x.status = :status)
              and (cast(:coachId as bigint) is null or x.coach_profile_id = :coachId)
              and (cast(:studentId as bigint) is null or x.student_user_id = :studentId)
              and (cast(:from as timestamptz) is null or x.start_time >= :from)
              and (cast(:to as timestamptz) is null or x.start_time < :to)
            """, nativeQuery = true)
    Page<AdminSessionView> searchAdminOperations(@Param("type") String type,
                                                 @Param("status") String status,
                                                 @Param("coachId") Long coachId,
                                                 @Param("studentId") Long studentId,
                                                 @Param("from") Instant from,
                                                 @Param("to") Instant to,
                                                 Pageable pageable);
}
