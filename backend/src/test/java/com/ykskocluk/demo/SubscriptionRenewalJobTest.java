package com.ykskocluk.demo;

import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.Package;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.entity.University;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.SubscriptionStatus;
import com.ykskocluk.demo.integration.ChargeResult;
import com.ykskocluk.demo.integration.IyzicoClient;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.PackageRepository;
import com.ykskocluk.demo.repository.PaymentRepository;
import com.ykskocluk.demo.repository.SubscriptionRepository;
import com.ykskocluk.demo.repository.UniversityRepository;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.service.SubscriptionRenewalJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 8c renewal job — due-set selection and the retry-boundary terminal case. The {@link IyzicoClient}
 * charge is mocked so success/failure is controlled. Proves the job processes only ACTIVE-due + PAST_DUE,
 * leaves not-due and terminal (EXPIRED/CANCELLED) subscriptions untouched, and that a retry-exhausted sub
 * EXPIRES and is NOT re-attempted on a later run.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SubscriptionRenewalJobTest {

    private static final Instant NOW = Instant.parse("2026-07-01T09:00:00Z");

    @Autowired SubscriptionRenewalJob job;
    @Autowired UserRepository userRepository;
    @Autowired UniversityRepository universityRepository;
    @Autowired CoachProfileRepository coachProfileRepository;
    @Autowired PackageRepository packageRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired PaymentRepository paymentRepository;

    @MockitoBean IyzicoClient iyzicoClient;

    private CoachProfile coach(int activeCount) {
        University uni = new University();
        uni.setName("Uni " + System.nanoTime());
        universityRepository.save(uni);
        User u = TestUsers.create(userRepository, Role.COACH,
                "coach-" + System.nanoTime() + "@example.com", "Coach");
        CoachProfile c = new CoachProfile();
        c.setUser(u);
        c.setUniversity(uni);
        c.setHeadline("Koç");
        c.setStatus(CoachProfileStatus.APPROVED);
        c.setMaxStudentCapacity(20);
        c.setActiveStudentCount(activeCount);
        return coachProfileRepository.save(c);
    }

    private Subscription sub(CoachProfile coach, SubscriptionStatus status, Instant endAt,
                            int failedCount, boolean autoRenew) {
        User student = TestUsers.create(userRepository, Role.STUDENT,
                "stu-" + System.nanoTime() + "@example.com", "Student");
        Package pkg = packageRepository.findByActiveTrueOrderByPriceAsc().get(0);
        Subscription s = new Subscription();
        s.setStudent(student);
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

    private Subscription reload(Subscription s) {
        return subscriptionRepository.findById(s.getId()).orElseThrow();
    }

    private int paymentCount(Subscription s) {
        return paymentRepository.findBySubscriptionIdOrderByCreatedAtDesc(s.getId()).size();
    }

    @Test
    void runRenewals_processesDueAndPastDue_leavesNotDueAndTerminalUntouched() {
        when(iyzicoClient.charge(any(), any(), any())).thenReturn(new ChargeResult(true, "ref"));
        CoachProfile coach = coach(5);

        Instant dueEnd = NOW.minus(1, ChronoUnit.HOURS);
        Instant futureEnd = NOW.plus(10, ChronoUnit.DAYS);
        Subscription activeDue = sub(coach, SubscriptionStatus.ACTIVE, dueEnd, 0, true);
        Subscription activeFuture = sub(coach, SubscriptionStatus.ACTIVE, futureEnd, 0, true);
        Subscription pastDue = sub(coach, SubscriptionStatus.PAST_DUE, NOW.minus(2, ChronoUnit.DAYS), 1, true);
        Subscription cancelledExpiring = sub(coach, SubscriptionStatus.ACTIVE, dueEnd, 0, false); // auto-renew off, due
        Subscription expired = sub(coach, SubscriptionStatus.EXPIRED, NOW.minus(40, ChronoUnit.DAYS), 3, true);
        Subscription cancelled = sub(coach, SubscriptionStatus.CANCELLED, NOW.minus(40, ChronoUnit.DAYS), 0, false);

        job.runRenewals(NOW);

        // ACTIVE-due → renewed (charged, end advanced one period)
        assertThat(reload(activeDue).getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(reload(activeDue).getEndAt()).isEqualTo(dueEnd.plus(30, ChronoUnit.DAYS));
        assertThat(paymentCount(activeDue)).isEqualTo(1);

        // PAST_DUE → retried back to ACTIVE
        assertThat(reload(pastDue).getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(paymentCount(pastDue)).isEqualTo(1);

        // auto-renew off + due → EXPIRED without any charge
        assertThat(reload(cancelledExpiring).getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(paymentCount(cancelledExpiring)).isZero();

        // NOT due → untouched (not selected)
        assertThat(reload(activeFuture).getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(reload(activeFuture).getEndAt()).isEqualTo(futureEnd);
        assertThat(paymentCount(activeFuture)).isZero();

        // Terminal → never selected, never touched
        assertThat(reload(expired).getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(reload(cancelled).getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(paymentCount(expired)).isZero();
        assertThat(paymentCount(cancelled)).isZero();
    }

    @Test
    void pastDueAtRetryBoundary_expires_andIsNotReattemptedOnNextRun() {
        when(iyzicoClient.charge(any(), any(), any())).thenReturn(new ChargeResult(false, null));
        CoachProfile coach = coach(1);
        Subscription boundary = sub(coach, SubscriptionStatus.PAST_DUE,
                NOW.minus(2, ChronoUnit.DAYS), 2, true); // count=2, one more failure exhausts retryDays=3

        // Per-subscription key (leftovers from sibling tests share this DB; assert only THIS sub).
        String boundaryKey = "charge:" + boundary.getId() + ":2026-07-01";

        job.runRenewals(NOW); // 3rd failure → EXPIRED, not another PAST_DUE

        assertThat(reload(boundary).getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(paymentCount(boundary)).isEqualTo(1);
        verify(iyzicoClient, times(1)).charge(any(), any(), eq(boundaryKey)); // charged exactly once

        // A retry-exhausted (now EXPIRED) sub is NOT in the due set → a later run does not re-charge it.
        job.runRenewals(NOW);

        assertThat(reload(boundary).getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(paymentCount(boundary)).isEqualTo(1);                     // still one row
        verify(iyzicoClient, times(1)).charge(any(), any(), eq(boundaryKey)); // still once — not re-attempted
    }
}
