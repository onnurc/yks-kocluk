package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.CoachAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CoachAvailabilityRepository extends JpaRepository<CoachAvailability, Long> {

    /** A coach's own slots (all states), earliest first. */
    List<CoachAvailability> findByCoachProfileIdOrderByStartTimeAsc(Long coachProfileId);

    /** Student-facing view: a coach's open (unbooked) future slots, earliest first. */
    List<CoachAvailability> findByCoachProfileIdAndBookedFalseAndStartTimeAfterOrderByStartTimeAsc(
            Long coachProfileId, Instant after);

    /** Ownership-scoped lookup for delete — only matches the coach's own slot. */
    Optional<CoachAvailability> findByIdAndCoachProfileId(Long id, Long coachProfileId);
}
