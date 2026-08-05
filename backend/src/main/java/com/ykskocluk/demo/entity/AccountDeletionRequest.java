package com.ykskocluk.demo.entity;

import com.ykskocluk.demo.enums.AccountDeletionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "account_deletion_requests")
@Getter @Setter @NoArgsConstructor
public class AccountDeletionRequest extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountDeletionStatus status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "processing_source", nullable = false, length = 50)
    private String processingSource;

    @Column(name = "identity_email_hash", nullable = false, length = 64)
    private String identityEmailHash;

    @Column(name = "oauth_subject_hash", length = 64)
    private String oauthSubjectHash;
}
