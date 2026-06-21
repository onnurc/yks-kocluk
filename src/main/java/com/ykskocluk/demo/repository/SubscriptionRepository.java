package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    boolean existsByStudentIdAndCoachProfileIdAndStatus(Long studentId, Long coachProfileId, SubscriptionStatus status);

    /** The active subscription that authorizes booking. */
    Optional<Subscription> findByStudentIdAndCoachProfileIdAndStatus(
            Long studentId, Long coachProfileId, SubscriptionStatus status);

    /**
     * Message gate: true if the student has EVER subscribed to this coach (any status —
     * active or past). Never-subscribed → no messaging. Enforced server-side.
     */
    boolean existsByStudentIdAndCoachProfileId(Long studentId, Long coachProfileId);
}
