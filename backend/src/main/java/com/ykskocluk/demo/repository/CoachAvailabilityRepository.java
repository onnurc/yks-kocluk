package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.CoachAvailability;
import com.ykskocluk.demo.enums.AvailabilityPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CoachAvailabilityRepository extends JpaRepository<CoachAvailability, Long> {

    /** A coach's own slots (all states), earliest first. */
    List<CoachAvailability> findByCoachProfileIdOrderByStartTimeAsc(Long coachProfileId);

    /** Student-facing view: a coach's open (unbooked) future slots, earliest first. */
    List<CoachAvailability> findByCoachProfileIdAndBookedFalseAndStartTimeAfterOrderByStartTimeAsc(
            Long coachProfileId, Instant after);

    List<CoachAvailability> findByCoachProfileIdAndPurposeAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
            Long coachProfileId, AvailabilityPurpose purpose, Instant from, Instant to);

    List<CoachAvailability> findByCoachProfileIdAndPurposeAndBookedFalseAndStartTimeGreaterThanAndStartTimeLessThanOrderByStartTimeAsc(
            Long coachProfileId, AvailabilityPurpose purpose, Instant from, Instant to);

    boolean existsByCoachProfileIdAndBookedFalseAndStartTimeAfter(Long coachProfileId, Instant after);

    /** Ownership-scoped lookup for delete — only matches the coach's own slot. */
    Optional<CoachAvailability> findByIdAndCoachProfileId(Long id, Long coachProfileId);

    /**
     * Scalar lookup used before the booking locks are acquired. Returning only the coach id is
     * intentional: loading the versioned availability entity here would leave a potentially stale
     * managed instance in the persistence context before {@link #findByIdForUpdate(Long)} waits for
     * a concurrent booking to commit.
     */
    @Query("select a.coachProfile.id from CoachAvailability a where a.id = :id")
    Optional<Long> findCoachProfileIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CoachAvailability a where a.id = :id")
    Optional<CoachAvailability> findByIdForUpdate(@Param("id") Long id);

    /**
     * Returns true if any existing slot for the coach overlaps the given [startTime, endTime) range.
     * Overlap formula: existing.startTime &lt; newEndTime AND existing.endTime &gt; newStartTime.
     */
    @Query("""
            select count(a) > 0 from CoachAvailability a
             where a.coachProfile.id = :coachProfileId
               and a.startTime < :endTime
               and a.endTime > :startTime
            """)
    boolean existsOverlapping(@Param("coachProfileId") Long coachProfileId,
                              @Param("startTime") Instant startTime,
                              @Param("endTime") Instant endTime);
}
