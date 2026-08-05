package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.LegalAcceptance;
import com.ykskocluk.demo.enums.LegalAcceptanceType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalAcceptanceRepository extends JpaRepository<LegalAcceptance, Long> {
    boolean existsByUserIdAndLegalDocumentIdAndAcceptanceTypeAndSourceAndWithdrawnAtIsNull(
            Long userId, Long documentId, LegalAcceptanceType acceptanceType, String source);
}
