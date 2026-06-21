package com.ykskocluk.demo.entity;

import com.ykskocluk.demo.enums.CoachProfileStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A coach's public profile. One per coach {@link User}. Bookable only when
 * {@code status == APPROVED} (enforced in search/booking phases). No price field —
 * pricing is uniform/platform-set. Rating and total sessions are derived at runtime,
 * never stored.
 */
@Entity
@Table(name = "coach_profiles")
@Getter
@Setter
@NoArgsConstructor
public class CoachProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 150)
    private String headline;

    @Column(length = 2000)
    private String bio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "university_id", nullable = false)
    private University university;

    @Column(length = 150)
    private String department;

    @Column(name = "graduation_year")
    private Integer graduationYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CoachProfileStatus status;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "active_student_count", nullable = false)
    private int activeStudentCount;

    @Column(name = "max_student_capacity", nullable = false)
    private int maxStudentCapacity;

    @Column(name = "payout_account_ready", nullable = false)
    private boolean payoutAccountReady;
}
