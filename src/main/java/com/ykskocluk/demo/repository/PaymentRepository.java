package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    List<Payment> findBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);
}
