package com.ykskocluk.demo.entity;

import com.ykskocluk.demo.enums.LegalAcceptanceType;
import com.ykskocluk.demo.enums.LegalDocumentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "legal_acceptances")
@Getter @Setter @NoArgsConstructor
public class LegalAcceptance extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "legal_document_id", nullable = false)
    private LegalDocument legalDocument;
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 60)
    private LegalDocumentType documentType;
    @Column(name = "document_version", nullable = false, length = 50)
    private String documentVersion;
    @Column(name = "document_content_hash", nullable = false, length = 64)
    private String documentContentHash;
    @Enumerated(EnumType.STRING)
    @Column(name = "acceptance_type", nullable = false, length = 30)
    private LegalAcceptanceType acceptanceType;
    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;
    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;
    @Column(nullable = false, length = 50)
    private String source;

    /** Checkout evidence linkage; null for registration/onboarding evidence. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    /** Exact charge attempt this evidence preceded; null for non-checkout evidence. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;
}
