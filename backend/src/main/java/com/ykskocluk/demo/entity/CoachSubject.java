package com.ykskocluk.demo.entity;

import com.ykskocluk.demo.enums.Track;
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

/**
 * Join row: which {@link Track} a coach mentors. The track-level filter table used by
 * search — matching is track-level, not lesson-level. Unique per (coach_profile, track).
 */
@Entity
@Table(name = "coach_subjects")
@Getter
@Setter
@NoArgsConstructor
public class CoachSubject extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_profile_id", nullable = false)
    private CoachProfile coachProfile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Track track;
}
