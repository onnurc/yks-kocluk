package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.Payment;
import com.ykskocluk.demo.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    List<Payment> findBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);

    /** Crash-recovery: an unreconciled in-flight attempt to resume (re-charge with the same key). */
    Optional<Payment> findFirstBySubscriptionIdAndStatus(Long subscriptionId, PaymentStatus status);

    List<Payment> findBySourcePaymentIdAndStatus(Long sourcePaymentId, PaymentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);
}
