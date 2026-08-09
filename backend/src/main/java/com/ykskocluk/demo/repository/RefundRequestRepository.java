package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.RefundRequest;
import com.ykskocluk.demo.enums.RefundRequestStatus;
import com.ykskocluk.demo.enums.RefundWindow;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {
    boolean existsByOriginalPaymentIdAndStatusIn(Long paymentId, Collection<RefundRequestStatus> statuses);

    @EntityGraph(attributePaths = {"subscription.coachProfile.user", "subscription.pkg", "originalPayment", "refundPayment"})
    Page<RefundRequest> findByStudentIdOrderByRequestedAtDesc(Long studentId, Pageable pageable);

    @EntityGraph(attributePaths = {"student", "subscription.coachProfile.user", "subscription.pkg", "originalPayment", "refundPayment"})
    @Query("""
            select r from RefundRequest r
             where (:status is null or r.status = :status)
               and (:window is null or r.refundWindow = :window)
               and (:studentId is null or r.student.id = :studentId)
               and (:coachId is null or r.subscription.coachProfile.id = :coachId)
               and (:from is null or r.requestedAt >= :from)
               and (:to is null or r.requestedAt < :to)
            """)
    Page<RefundRequest> searchAdmin(@Param("status") RefundRequestStatus status,
                                    @Param("window") RefundWindow window,
                                    @Param("studentId") Long studentId,
                                    @Param("coachId") Long coachId,
                                    @Param("from") Instant from, @Param("to") Instant to,
                                    Pageable pageable);

    @EntityGraph(attributePaths = {"student", "subscription.coachProfile.user", "subscription.pkg", "originalPayment", "refundPayment"})
    @Query("select r from RefundRequest r where r.id = :id")
    Optional<RefundRequest> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefundRequest r where r.id = :id")
    Optional<RefundRequest> findByIdForUpdate(@Param("id") Long id);
}
