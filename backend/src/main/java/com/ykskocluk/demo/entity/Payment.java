package com.ykskocluk.demo.entity;

import com.ykskocluk.demo.enums.PaymentStatus;
import com.ykskocluk.demo.enums.PaymentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One charge attempt against a {@link Subscription} (Phase 8). A row is reserved {@code PENDING}
 * with a UNIQUE {@code idempotencyKey} before the external charge, then finalized SUCCESS/FAILED.
 *
 * <p>Commission is <strong>snapshotted per row</strong> ({@code commissionRate / commissionAmount /
 * coachPayoutAmount}) at charge time. The actual payout/fund distribution to coaches is an
 * <strong>isolated SEAM</strong> (model A vs B pending) — not implemented in Stage 1. A future
 * REFUND is a new row ({@code type=REFUND}, {@code sourcePayment} linking the original), never a
 * mutation of this one.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentType type;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    // UNIQUE — the double-charge guard (same philosophy as UNIQUE(availability_id) for bookings).
    @Column(name = "idempotency_key", nullable = false, length = 200, unique = true)
    private String idempotencyKey;

    // The provider's payment reference (stub ref in Stage 1; real iyzico ref in Stage 2). Null while PENDING.
    @Column(name = "provider_reference", length = 255)
    private String providerReference;

    @Column(name = "succeeded_at")
    private Instant succeededAt;

    // Commission snapshot at transaction time (CLAUDE.md). Rate is a fraction, e.g. 0.2000.
    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal commissionRate;

    @Column(name = "commission_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal commissionAmount;

    @Column(name = "coach_payout_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal coachPayoutAmount;

    // SEAM: refund linkage. Always null in Stage 1; a REFUND row will point at the original CHARGE.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_payment_id")
    private Payment sourcePayment;
}
