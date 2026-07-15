package com.ykskocluk.demo.entity;

import com.ykskocluk.demo.enums.SessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A booked coaching session. The double-booking guarantee is the UNIQUE on
 * {@code availability_id} (DB-level) — an insert collision is what proves a slot is
 * taken, never a read of {@code is_booked}. {@code availability} is nullable so an early
 * cancel can free the slot (Phase 4d); start/end are snapshotted off the slot so the
 * session keeps its time after the link is cleared.
 */
@Entity
@Table(name = "sessions")
@Getter
@Setter
@NoArgsConstructor
public class Session extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_user_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_profile_id", nullable = false)
    private CoachProfile coachProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    // Nullable + UNIQUE: the double-booking guard at insert; cleared on early cancel (4d).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "availability_id", unique = true)
    private CoachAvailability availability;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    // Set after commit by the notification listener (Phase 4d); null until then.
    @Column(name = "meet_link", length = 500)
    private String meetLink;
}
