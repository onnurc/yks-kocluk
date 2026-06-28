package com.ykskocluk.demo;

import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.Package;
import com.ykskocluk.demo.entity.Payment;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.entity.University;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.PaymentStatus;
import com.ykskocluk.demo.enums.PaymentType;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.SubscriptionStatus;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.integration.ChargeResult;
import com.ykskocluk.demo.integration.IyzicoClient;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.PackageRepository;
import com.ykskocluk.demo.repository.PaymentRepository;
import com.ykskocluk.demo.repository.SubscriptionRepository;
import com.ykskocluk.demo.repository.UniversityRepository;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.service.BillingOutcome;
import com.ykskocluk.demo.service.SubscriptionBillingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 8b billing lifecycle — every branch on real PostgreSQL, with the {@link IyzicoClient}
 * charge mocked so success/failure is controlled per test. Covers renewal success (+commission
 * snapshot), fail→PAST_DUE, retry→ACTIVE, retry-exhaustion→EXPIRED (+atomic capacity decrement,
 * floored at 0), cancel→expire-without-charge, and the crash-recovery resume path.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SubscriptionBillingServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-01T09:00:00Z");

    @Autowired SubscriptionBillingService billingService;
    @Autowired UserRepository userRepository;
    @Autowired UniversityRepository universityRepository;
    @Autowired CoachProfileRepository coachProfileRepository;
    @Autowired PackageRepository packageRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired PaymentRepository paymentRepository;

    @MockitoBean IyzicoClient iyzicoClient;

    // --- seeding ---

    private CoachProfile coach(int activeCount) {
        University uni = new University();
        uni.setName("Uni " + System.nanoTime());
        universityRepository.save(uni);
        User u = new User();
        u.setEmail("coach-" + System.nanoTime() + "@example.com");
        u.setFullName("Coach");
        u.setRole(Role.COACH);
        u.setStatus(UserStatus.ACTIVE);
        userRepository.save(u);
        CoachProfile c = new CoachProfile();
        c.setUser(u);
        c.setUniversity(uni);
        c.setHeadline("Koç");
        c.setStatus(CoachProfileStatus.APPROVED);
        c.setMaxStudentCapacity(10);
        c.setActiveStudentCount(activeCount);
        return coachProfileRepository.save(c);
    }

    private User student() {
        User u = new User();
        u.setEmail("stu-" + System.nanoTime() + "@example.com");
        u.setFullName("Student");
        u.setRole(Role.STUDENT);
        u.setStatus(UserStatus.ACTIVE);
        return userRepository.save(u);
    }

    private Subscription sub(CoachProfile coach, SubscriptionStatus status, Instant endAt,
                            int failedCount, boolean autoRenew) {
        Package pkg = packageRepository.findByActiveTrueOrderByPriceAsc().get(0); // Aylık 1x: 1500.00 / 30d
        Subscription s = new Subscription();
        s.setStudent(student());
        s.setCoachProfile(coach);
        s.setPkg(pkg);
        s.setStatus(status);
        s.setStartAt(NOW.minus(40, ChronoUnit.DAYS));
        s.setEndAt(endAt);
        s.setFailedChargeCount(failedCount);
        s.setAutoRenew(autoRenew);
        s.setSavedCardToken("stub-card-token-x");
        return subscriptionRepository.save(s);
    }

    private List<Payment> payments(Long subId) {
        return paymentRepository.findBySubscriptionIdOrderByCreatedAtDesc(subId);
    }

    // --- branches ---

    @Test
    void renewalSuccess_advancesEndAt_resetsFailures_snapshotsCommission() {
        Instant oldEnd = NOW.minus(1, ChronoUnit.HOURS);          // due
        Subscription s = sub(coach(1), SubscriptionStatus.ACTIVE, oldEnd, 0, true);
        when(iyzicoClient.charge(any(), any(), any())).thenReturn(new ChargeResult(true, "ref-1"));

        BillingOutcome outcome = billingService.processDue(s.getId(), NOW);

        assertThat(outcome).isEqualTo(BillingOutcome.CHARGED_SUCCESS);
        Subscription reloaded = subscriptionRepository.findById(s.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(reloaded.getEndAt()).isEqualTo(oldEnd.plus(30, ChronoUnit.DAYS)); // +durationDays
        assertThat(reloaded.getFailedChargeCount()).isZero();

        List<Payment> rows = payments(s.getId());
        assertThat(rows).hasSize(1);
        Payment p = rows.get(0);
        assertThat(p.getType()).isEqualTo(PaymentType.CHARGE);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(p.getProviderReference()).isEqualTo("ref-1");
        assertThat(p.getAmount()).isEqualByComparingTo("1500.00");
        assertThat(p.getCommissionRate()).isEqualByComparingTo("0.20");
        assertThat(p.getCommissionAmount()).isEqualByComparingTo("300.00");   // 1500 * 0.20
        assertThat(p.getCoachPayoutAmount()).isEqualByComparingTo("1200.00"); // 1500 - 300
    }

    @Test
    void chargeFails_movesToPastDue_endAtUnchanged() {
        Instant oldEnd = NOW.minus(1, ChronoUnit.HOURS);
        Subscription s = sub(coach(1), SubscriptionStatus.ACTIVE, oldEnd, 0, true);
        when(iyzicoClient.charge(any(), any(), any())).thenReturn(new ChargeResult(false, null));

        BillingOutcome outcome = billingService.processDue(s.getId(), NOW);

        assertThat(outcome).isEqualTo(BillingOutcome.CHARGED_FAILED_PAST_DUE);
        Subscription reloaded = subscriptionRepository.findById(s.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(reloaded.getFailedChargeCount()).isEqualTo(1);
        assertThat(reloaded.getEndAt()).isEqualTo(oldEnd);
        assertThat(reloaded.getLastChargeAttemptAt()).isEqualTo(NOW);
        assertThat(payments(s.getId())).singleElement()
                .satisfies(p -> assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED));
    }

    @Test
    void retrySuccess_returnsToActive_andAdvances() {
        Instant oldEnd = NOW.minus(2, ChronoUnit.DAYS);
        Subscription s = sub(coach(1), SubscriptionStatus.PAST_DUE, oldEnd, 1, true);
        when(iyzicoClient.charge(any(), any(), any())).thenReturn(new ChargeResult(true, "ref-2"));

        BillingOutcome outcome = billingService.processDue(s.getId(), NOW);

        assertThat(outcome).isEqualTo(BillingOutcome.CHARGED_SUCCESS);
        Subscription reloaded = subscriptionRepository.findById(s.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(reloaded.getFailedChargeCount()).isZero();
        assertThat(reloaded.getEndAt()).isEqualTo(oldEnd.plus(30, ChronoUnit.DAYS));
    }

    @Test
    void retryExhaustion_expires_andDecrementsCapacity() {
        CoachProfile coach = coach(1);
        Subscription s = sub(coach, SubscriptionStatus.PAST_DUE, NOW.minus(2, ChronoUnit.DAYS), 2, true);
        when(iyzicoClient.charge(any(), any(), any())).thenReturn(new ChargeResult(false, null)); // 3rd fail

        BillingOutcome outcome = billingService.processDue(s.getId(), NOW);

        assertThat(outcome).isEqualTo(BillingOutcome.CHARGED_FAILED_EXPIRED);
        assertThat(subscriptionRepository.findById(s.getId()).orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(coachProfileRepository.findById(coach.getId()).orElseThrow().getActiveStudentCount())
                .isEqualTo(0); // 1 -> 0
    }

    @Test
    void capacityDecrement_flooredAtZero() {
        CoachProfile coach = coach(0); // already 0 (artificial) — the WHERE active>0 guard must hold
        Subscription s = sub(coach, SubscriptionStatus.PAST_DUE, NOW.minus(2, ChronoUnit.DAYS), 2, true);
        when(iyzicoClient.charge(any(), any(), any())).thenReturn(new ChargeResult(false, null));

        billingService.processDue(s.getId(), NOW);

        assertThat(coachProfileRepository.findById(coach.getId()).orElseThrow().getActiveStudentCount())
                .isEqualTo(0); // not -1
    }

    @Test
    void cancel_keepsLiveUntilEndAt_thenExpiresWithoutCharge() {
        CoachProfile coach = coach(1);
        Subscription s = sub(coach, SubscriptionStatus.ACTIVE, NOW.plus(10, ChronoUnit.DAYS), 0, true);

        billingService.cancel(s.getId(), s.getStudent().getId());

        Subscription afterCancel = subscriptionRepository.findById(s.getId()).orElseThrow();
        assertThat(afterCancel.isAutoRenew()).isFalse();
        assertThat(afterCancel.getCancelledAt()).isNotNull();
        assertThat(afterCancel.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE); // still live
        assertThat(afterCancel.getEndAt()).isEqualTo(NOW.plus(10, ChronoUnit.DAYS));

        // Reach end_at: a cancelled sub past end_at expires with NO charge.
        afterCancel.setEndAt(NOW.minus(1, ChronoUnit.HOURS));
        subscriptionRepository.save(afterCancel);

        BillingOutcome outcome = billingService.processDue(s.getId(), NOW);

        assertThat(outcome).isEqualTo(BillingOutcome.EXPIRED_NO_RENEW);
        assertThat(subscriptionRepository.findById(s.getId()).orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(payments(s.getId())).isEmpty();                         // no Payment row
        verify(iyzicoClient, never()).charge(any(), any(), any());          // no charge at all
        assertThat(coachProfileRepository.findById(coach.getId()).orElseThrow().getActiveStudentCount())
                .isEqualTo(0);
    }

    @Test
    void crashRecovery_resumesStalePending_withSameKey_noNewRow() {
        Subscription s = sub(coach(1), SubscriptionStatus.ACTIVE, NOW.minus(1, ChronoUnit.HOURS), 0, true);

        // Simulate a crash on a PRIOR day: a PENDING row left behind (charge never finalized).
        String staleKey = "charge:" + s.getId() + ":2026-06-30"; // yesterday relative to NOW
        Payment stale = new Payment();
        stale.setSubscription(s);
        stale.setType(PaymentType.CHARGE);
        stale.setStatus(PaymentStatus.PENDING);
        stale.setAmount(new BigDecimal("1500.00"));
        stale.setIdempotencyKey(staleKey);
        stale.setCommissionRate(new BigDecimal("0.2000"));
        stale.setCommissionAmount(new BigDecimal("300.00"));
        stale.setCoachPayoutAmount(new BigDecimal("1200.00"));
        paymentRepository.saveAndFlush(stale);

        when(iyzicoClient.charge(any(), any(), eq(staleKey))).thenReturn(new ChargeResult(true, "ref-resumed"));

        BillingOutcome outcome = billingService.processDue(s.getId(), NOW);

        assertThat(outcome).isEqualTo(BillingOutcome.CHARGED_SUCCESS);
        // The stale row was RESUMED (re-charged with its OWN key), not replaced by a new today-row.
        verify(iyzicoClient).charge(any(), any(), eq(staleKey));
        List<Payment> rows = payments(s.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getIdempotencyKey()).isEqualTo(staleKey);
        assertThat(rows.get(0).getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(rows.get(0).getProviderReference()).isEqualTo("ref-resumed");
    }

    @Test
    void todaysInFlightPending_isSkipped_notResumed_andNotCharged() {
        Subscription s = sub(coach(1), SubscriptionStatus.ACTIVE, NOW.minus(1, ChronoUnit.HOURS), 0, true);

        // Another run has already reserved TODAY's attempt and is mid-charge: a PENDING row with
        // today's key (NOW is 2026-07-01T09:00Z → 2026-07-01 in Europe/Istanbul).
        String todayKey = "charge:" + s.getId() + ":2026-07-01";
        Payment inFlight = new Payment();
        inFlight.setSubscription(s);
        inFlight.setType(PaymentType.CHARGE);
        inFlight.setStatus(PaymentStatus.PENDING);
        inFlight.setAmount(new BigDecimal("1500.00"));
        inFlight.setIdempotencyKey(todayKey);
        inFlight.setCommissionRate(new BigDecimal("0.2000"));
        inFlight.setCommissionAmount(new BigDecimal("300.00"));
        inFlight.setCoachPayoutAmount(new BigDecimal("1200.00"));
        paymentRepository.saveAndFlush(inFlight);

        BillingOutcome outcome = billingService.processDue(s.getId(), NOW);

        // today's-PENDING → back off (distinct from prior-day-PENDING → resume). The other run owns it.
        assertThat(outcome).isEqualTo(BillingOutcome.SKIPPED_ALREADY_PROCESSED);
        verify(iyzicoClient, never()).charge(any(), any(), any());          // never charged
        List<Payment> rows = payments(s.getId());
        assertThat(rows).hasSize(1);                                        // no new row created
        assertThat(rows.get(0).getIdempotencyKey()).isEqualTo(todayKey);
        assertThat(rows.get(0).getStatus()).isEqualTo(PaymentStatus.PENDING); // left untouched for the owner
    }
}
