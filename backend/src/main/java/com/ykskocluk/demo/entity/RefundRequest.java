package com.ykskocluk.demo.entity;

import com.ykskocluk.demo.enums.RefundRequestStatus;
import com.ykskocluk.demo.enums.RefundWindow;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "refund_requests")
@Getter
@Setter
@NoArgsConstructor
public class RefundRequest extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_user_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_payment_id", nullable = false)
    private Payment originalPayment;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "purchase_at", nullable = false)
    private Instant purchaseAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_window", nullable = false, length = 30)
    private RefundWindow refundWindow;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundRequestStatus status;

    @Column(name = "service_started", nullable = false)
    private boolean serviceStarted;

    @Column(name = "paid_sessions_count", nullable = false)
    private int paidSessionsCount;

    @Column(name = "completed_sessions_count", nullable = false)
    private int completedSessionsCount;

    @Column(name = "earliest_paid_session_at")
    private Instant earliestPaidSessionAt;

    @Column(name = "latest_relevant_session_status", length = 20)
    private String latestRelevantSessionStatus;

    @Column(name = "admin_decision_at")
    private Instant adminDecisionAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_decision_by")
    private User adminDecisionBy;

    @Column(name = "rejection_reason", length = 2000)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_payment_id")
    private Payment refundPayment;
}
