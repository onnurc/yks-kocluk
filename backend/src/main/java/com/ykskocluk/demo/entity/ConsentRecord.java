package com.ykskocluk.demo.entity;

import com.ykskocluk.demo.enums.ConsentType;
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
 * Audit record of a user's legal document acceptance (KVKK, TERMS, etc).
 */
@Entity
@Table(name = "consent_records")
@Getter
@Setter
@NoArgsConstructor
public class ConsentRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 50)
    private ConsentType consentType;

    @Column(name = "document_version", nullable = false, length = 50)
    private String documentVersion;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    @Column(name = "ip_address", length = 100)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;
}
