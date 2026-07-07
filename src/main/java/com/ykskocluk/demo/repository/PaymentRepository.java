package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.Payment;
import com.ykskocluk.demo.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    List<Payment> findBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);

    /** Crash-recovery: an unreconciled in-flight attempt to resume (re-charge with the same key). */
    Optional<Payment> findFirstBySubscriptionIdAndStatus(Long subscriptionId, PaymentStatus status);

    List<Payment> findBySourcePaymentIdAndStatus(Long sourcePaymentId, PaymentStatus status);
}
