package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.ConsentRecord;
import com.ykskocluk.demo.enums.ConsentType;
import com.ykskocluk.demo.enums.ConsentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * Repository for {@link ConsentRecord}.
 */
public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, Long> {
    boolean existsByUserIdAndConsentTypeAndDocumentVersion(Long userId, ConsentType consentType, String documentVersion);
    boolean existsByUserIdAndConsentTypeAndDocumentVersionAndStatus(Long userId, ConsentType consentType, String documentVersion, ConsentStatus status);
    Optional<ConsentRecord> findFirstByUserIdAndConsentTypeOrderByAcceptedAtDesc(Long userId, ConsentType consentType);
}
