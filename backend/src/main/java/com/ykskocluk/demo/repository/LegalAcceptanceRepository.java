package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.LegalAcceptance;
import com.ykskocluk.demo.enums.LegalAcceptanceType;
import com.ykskocluk.demo.enums.LegalDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface LegalAcceptanceRepository extends JpaRepository<LegalAcceptance, Long> {
    boolean existsByUserIdAndLegalDocumentIdAndAcceptanceTypeAndSourceAndWithdrawnAtIsNull(
            Long userId, Long documentId, LegalAcceptanceType acceptanceType, String source);

    boolean existsBySubscriptionIdAndLegalDocumentIdAndAcceptanceTypeAndSourceAndWithdrawnAtIsNull(
            Long subscriptionId, Long documentId, LegalAcceptanceType acceptanceType, String source);

    List<LegalAcceptance> findBySubscriptionIdOrderByIdAsc(Long subscriptionId);

    List<LegalAcceptance> findByUserIdAndDocumentTypeAndAcceptanceTypeAndWithdrawnAtIsNull(
            Long userId, LegalDocumentType documentType, LegalAcceptanceType acceptanceType);

    Optional<LegalAcceptance> findFirstByUserIdAndDocumentTypeAndAcceptanceTypeAndWithdrawnAtIsNotNullOrderByWithdrawnAtDesc(
            Long userId, LegalDocumentType documentType, LegalAcceptanceType acceptanceType);

    @Modifying
    @Query("delete from LegalAcceptance a where a.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
