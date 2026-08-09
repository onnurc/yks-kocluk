package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.Payment;
import com.ykskocluk.demo.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.ykskocluk.demo.enums.PaymentType;
import org.springframework.data.jpa.repository.EntityGraph;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    List<Payment> findBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);

    Optional<Payment> findFirstBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);

    /** Crash-recovery: an unreconciled in-flight attempt to resume (re-charge with the same key). */
    Optional<Payment> findFirstBySubscriptionIdAndStatus(Long subscriptionId, PaymentStatus status);

    List<Payment> findBySourcePaymentIdAndStatus(Long sourcePaymentId, PaymentStatus status);

    /**
     * Refunds of a charge in any of the given statuses. Used when reserving a new refund: a
     * PENDING (in-flight) refund reservation consumes refundable headroom just like a SUCCESS
     * one, so both must be counted to prevent two concurrent refunds over-refunding the charge.
     */
    List<Payment> findBySourcePaymentIdAndStatusIn(Long sourcePaymentId, Collection<PaymentStatus> statuses);

    long countBySourcePaymentId(Long sourcePaymentId);

    Optional<Payment> findFirstBySubscriptionIdAndTypeAndStatusOrderBySucceededAtAsc(
            Long subscriptionId, PaymentType type, PaymentStatus status);

    /** Batch variant of {@link #findBySourcePaymentIdAndStatus} — avoids N+1 in admin listings. */
    List<Payment> findBySourcePaymentIdInAndStatus(List<Long> sourcePaymentIds, PaymentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
             where p.subscription.coachProfile.id = :coachId
               and p.subscription.pkg.id = :packageId
               and p.status = com.ykskocluk.demo.enums.PaymentStatus.SUCCESS
               and p.type = com.ykskocluk.demo.enums.PaymentType.CHARGE
            """)
    BigDecimal grossSalesForPackage(@Param("coachId") Long coachId,
                                    @Param("packageId") Long packageId);

    @Query("""
            select p.subscription.pkg.id as packageId, coalesce(sum(p.amount), 0) as grossSales
              from Payment p
             where p.subscription.coachProfile.id = :coachId
               and p.status = com.ykskocluk.demo.enums.PaymentStatus.SUCCESS
               and p.type = com.ykskocluk.demo.enums.PaymentType.CHARGE
             group by p.subscription.pkg.id
            """)
    List<PackageGrossSales> grossSalesByPackage(@Param("coachId") Long coachId);

    @EntityGraph(attributePaths = {"subscription.student", "subscription.coachProfile.user",
            "subscription.pkg", "sourcePayment"})
    @Query("""
            select p from Payment p
             where (:type is null or p.type = :type)
               and (:status is null or p.status = :status)
               and (:studentId is null or p.subscription.student.id = :studentId)
               and (:coachId is null or p.subscription.coachProfile.id = :coachId)
               and (:packageId is null or p.subscription.pkg.id = :packageId)
               and (:from is null or p.createdAt >= :from)
               and (:to is null or p.createdAt < :to)
            """)
    Page<Payment> searchAdmin(@Param("type") PaymentType type,
                              @Param("status") PaymentStatus status,
                              @Param("studentId") Long studentId,
                              @Param("coachId") Long coachId,
                              @Param("packageId") Long packageId,
                              @Param("from") Instant from,
                              @Param("to") Instant to,
                              Pageable pageable);

    @Query("""
            select count(p) from Payment p
             where p.type = :type and p.status = :status
               and (:from is null or p.createdAt >= :from)
               and (:to is null or p.createdAt < :to)
            """)
    long countForPeriod(@Param("type") PaymentType type, @Param("status") PaymentStatus status,
                        @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
             where p.type = :type and p.status = :status
               and (:from is null or p.createdAt >= :from)
               and (:to is null or p.createdAt < :to)
            """)
    BigDecimal sumForPeriod(@Param("type") PaymentType type, @Param("status") PaymentStatus status,
                            @Param("from") Instant from, @Param("to") Instant to);
}
