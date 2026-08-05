package com.ykskocluk.demo.entity;

import com.ykskocluk.demo.enums.LegalDocumentStatus;
import com.ykskocluk.demo.enums.LegalDocumentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "legal_documents")
@Getter @Setter @NoArgsConstructor
public class LegalDocument extends BaseEntity {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private LegalDocumentType type;
    @Column(name = "document_version", nullable = false, length = 50)
    private String documentVersion;
    @Column(nullable = false, length = 300)
    private String title;
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LegalDocumentStatus status;
    @Column(name = "required_for_registration", nullable = false)
    private boolean requiredForRegistration;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "effective_at")
    private Instant effectiveAt;
}
