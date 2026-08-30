package com.ykskocluk.demo.entity;

import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.enums.AccountOrigin;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

/**
 * Platform identity. One row per email (email is unique). A user may authenticate
 * with a password ({@code passwordHash} set), with Google ({@code googleSub} set),
 * or both (auto-linked by verified email). PII is never leaked via DTOs.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    /** BCrypt hash; null for Google-only accounts. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "password_version", nullable = false)
    private int passwordVersion;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    /** Google subject id; null until linked. Unique when present. */
    @Column(name = "google_sub", unique = true)
    private String googleSub;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "suspension_reason", length = 2000)
    private String suspensionReason;

    @Column(name = "date_of_birth")
    private java.time.LocalDate dateOfBirth;

    @Column(name = "legal_onboarding_completed", nullable = false)
    private boolean legalOnboardingCompleted = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_origin", nullable = false, length = 30)
    private AccountOrigin accountOrigin = AccountOrigin.LEGACY;
}
