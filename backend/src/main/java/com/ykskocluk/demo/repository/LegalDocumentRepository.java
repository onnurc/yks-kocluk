package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.LegalDocument;
import com.ykskocluk.demo.enums.LegalDocumentStatus;
import com.ykskocluk.demo.enums.LegalDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LegalDocumentRepository extends JpaRepository<LegalDocument, Long> {
    Optional<LegalDocument> findFirstByTypeAndStatusAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
            LegalDocumentType type, LegalDocumentStatus status, Instant now);
    List<LegalDocument> findByStatusAndEffectiveAtLessThanEqualOrderByTypeAsc(
            LegalDocumentStatus status, Instant now);
}
