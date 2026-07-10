package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.ConsentRecord;
import com.ykskocluk.demo.enums.ConsentType;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link ConsentRecord}.
 */
public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, Long> {
    boolean existsByUserIdAndConsentTypeAndDocumentVersion(Long userId, ConsentType consentType, String documentVersion);
}
