package com.ykskocluk.demo.entity;

import com.ykskocluk.demo.enums.CoachApplicationStatus;
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
 * A public "become a coach" application. Distinct from {@link CoachProfile}: this exists before
 * any {@link User} account does. Approval creates the User (see {@code CoachApplicationService});
 * approval also initializes an incomplete {@link CoachProfile} shell so authenticated coach
 * pages have a stable owner row. The coach completes its education/public-profile fields later.
 */
@Entity
@Table(name = "coach_applications")
@Getter
@Setter
@NoArgsConstructor
public class CoachApplication extends BaseEntity {

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    @Column
    private String phone;

    @Column(length = 2000)
    private String experience;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CoachApplicationStatus status = CoachApplicationStatus.PENDING;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    /** Set on approval — the User account created from this application. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_user_id")
    private User linkedUser;
}
