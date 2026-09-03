package com.ykskocluk.demo.entity;

import com.ykskocluk.demo.enums.SubscriptionStatus;
import com.ykskocluk.demo.enums.DiscountType;
import com.ykskocluk.demo.enums.PackageType;
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

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;

/**
 * A student's subscription to a coach under a {@link Package}. Source of the weekly
 * session quota and the Phase 5 message gate. Created ACTIVE directly in Phase 4;
 * Phase 8 introduces the payment-gated lifecycle.
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class Subscription extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_user_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_profile_id", nullable = false)
    private CoachProfile coachProfile;

    // 'package' is a reserved word, so the field is 'pkg' (column package_id).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "package_id", nullable = false)
    private Package pkg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    // Paid-through date AND the renewal trigger: on the end_at day the renewal job charges and,
    // on success, pushes end_at one period forward. No separate next_renewal_date (would duplicate this).
    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    // --- Phase 8 auto-renew lifecycle ---

    // Renews monthly until the user cancels. Cancel sets this false; the sub stays live until end_at.
    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew = true;

    // Saved-card seam: the stub stamps a fake token now; real iyzico tokenization replaces it in Stage 2.
    @Column(name = "saved_card_token", length = 255)
    private String savedCardToken;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    // Retry accounting for the grace window (PAST_DUE): one attempt/day, EXPIRED after retryDays.
    @Column(name = "failed_charge_count", nullable = false)
    private int failedChargeCount = 0;

    @Column(name = "last_charge_attempt_at")
    private Instant lastChargeAttemptAt;

    @Column(name = "termination_reason", length = 2000)
    private String terminationReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "package_type_snapshot", length = 30)
    private PackageType packageTypeSnapshot;

    @Column(name = "list_price_snapshot", precision = 12, scale = 2)
    private BigDecimal listPriceSnapshot;

    @Column(name = "effective_price_snapshot", precision = 12, scale = 2)
    private BigDecimal effectivePriceSnapshot;

    @Column(name = "campaign_title_snapshot", length = 120)
    private String campaignTitleSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type_snapshot", length = 20)
    private DiscountType discountTypeSnapshot;

    @Column(name = "discount_value_snapshot", precision = 12, scale = 2)
    private BigDecimal discountValueSnapshot;

    @Column(name = "one_month_base_price_snapshot", precision = 12, scale = 2)
    private BigDecimal oneMonthBasePriceSnapshot;

    @Column(name = "purchased_at")
    private Instant purchasedAt;

    @Column(name = "until_exam_months_remaining_snapshot")
    private Integer untilExamMonthsRemainingSnapshot;

    @Column(name = "yks_exam_year_snapshot")
    private Integer yksExamYearSnapshot;

    @Column(name = "yks_exam_date_snapshot")
    private LocalDate yksExamDateSnapshot;
}
