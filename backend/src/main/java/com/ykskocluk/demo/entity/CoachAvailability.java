package com.ykskocluk.demo.entity;

import com.ykskocluk.demo.enums.AvailabilityPurpose;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A concrete bookable time slot owned by a coach. No templates / generation — slots are
 * stored individually (see CLAUDE.md DO-NOTs). Times are UTC {@link Instant}. The
 * UNIQUE(coach_profile_id, start_time) constraint prevents overlapping duplicates at the
 * same start. {@code booked} is set when a Session reserves this slot (Phase 4c).
 */
@Entity
@Table(name = "coach_availabilities")
@Getter
@Setter
@NoArgsConstructor
public class CoachAvailability extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_profile_id", nullable = false)
    private CoachProfile coachProfile;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "is_booked", nullable = false)
    private boolean booked;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AvailabilityPurpose purpose = AvailabilityPurpose.PAID;
}
