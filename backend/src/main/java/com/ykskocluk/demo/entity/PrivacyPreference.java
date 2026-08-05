package com.ykskocluk.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "privacy_preferences")
@Getter @Setter @NoArgsConstructor
public class PrivacyPreference extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "analytics_allowed", nullable = false)
    private boolean analyticsAllowed;

    @Column(name = "marketing_allowed", nullable = false)
    private boolean marketingAllowed;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cookie_policy_document_id", nullable = false)
    private LegalDocument cookiePolicyDocument;

    @Column(name = "policy_version", nullable = false, length = 50)
    private String policyVersion;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(nullable = false, length = 50)
    private String source;
}
