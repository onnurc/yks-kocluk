package com.ykskocluk.demo.entity;

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
 * A one-to-one messaging thread between a student and a coach. Exactly one per pair
 * (UNIQUE(student, coach)). Only a student may open one, and only with a coach they have
 * an active or past Subscription with — enforced server-side in {@code MessageService}.
 *
 * <p>{@code lastMessageAt} is a deliberate performance denormalization (the inbox sorts by
 * it on a high-frequency screen) — updated in the same transaction as each message insert.
 */
@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
public class Conversation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_user_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_profile_id", nullable = false)
    private CoachProfile coachProfile;

    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt;
}
