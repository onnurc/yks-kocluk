package com.ykskocluk.demo;

import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.Package;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.entity.University;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.SubscriptionStatus;
import com.ykskocluk.demo.enums.UserStatus;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The renewal idempotency race (the money-critical test). Two runs process the SAME subscription
 * on the SAME day at the same instant. The guard is {@code UNIQUE(idempotency_key)} reserved before
 * the charge (mirrors {@code SessionDoubleBookingConcurrencyTest}): exactly ONE Payment row, exactly
 * ONE charge invocation, and the subscription advances exactly ONCE.
 *
 * <p>The spy wraps the real {@code StubIyzicoClient}, so the charge actually executes and is counted.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SubscriptionRenewalConcurrencyTest {

    @Autowired SubscriptionBillingService billingService;
    @Autowired UserRepository userRepository;
    @Autowired UniversityRepository universityRepository;
    @Autowired CoachProfileRepository coachProfileRepository;
    @Autowired PackageRepository packageRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired PaymentRepository paymentRepository;

    @MockitoSpyBean IyzicoClient iyzicoClient; // wraps the real StubIyzicoClient (always succeeds)

    private User user(String email, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setFullName("User");
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        return userRepository.save(u);
    }

    @Test
    void twoRunsSameSubscriptionSameDay_chargesExactlyOnce() throws Exception {
        University uni = new University();
        uni.setName("Renewal University " + System.nanoTime());
        universityRepository.save(uni);

        CoachProfile coach = new CoachProfile();
        coach.setUser(user("coach-rn-" + System.nanoTime() + "@example.com", Role.COACH));
        coach.setUniversity(uni);
        coach.setHeadline("Koç");
        coach.setStatus(CoachProfileStatus.APPROVED);
        coach.setMaxStudentCapacity(10);
        coach.setActiveStudentCount(1);
        coachProfileRepository.save(coach);

        Package pkg = packageRepository.findByActiveTrueOrderByPriceAsc().get(0);

        Instant now = Instant.parse("2026-07-01T09:00:00Z");
        Instant oldEnd = now.minus(1, ChronoUnit.HOURS); // due

        Subscription sub = new Subscription();
        sub.setStudent(user("stu-rn-" + System.nanoTime() + "@example.com", Role.STUDENT));
        sub.setCoachProfile(coach);
        sub.setPkg(pkg);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStartAt(now.minus(31, ChronoUnit.DAYS));
        sub.setEndAt(oldEnd);
        sub.setAutoRenew(true);
        sub.setSavedCardToken("stub-card-token-x");
        subscriptionRepository.save(sub);
        Long subId = sub.getId();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<BillingOutcome> run = () -> {
            start.await();
            return billingService.processDue(subId, now);
        };
        Future<BillingOutcome> f1 = pool.submit(run);
        Future<BillingOutcome> f2 = pool.submit(run);
        start.countDown(); // fire both at once

        BillingOutcome o1 = f1.get(20, TimeUnit.SECONDS);
        BillingOutcome o2 = f2.get(20, TimeUnit.SECONDS);
        pool.shutdown();

        // exactly one charge, exactly one Payment row
        verify(iyzicoClient, times(1)).charge(any(), any(), any());
        assertThat(paymentRepository.findBySubscriptionIdOrderByCreatedAtDesc(subId)).hasSize(1);

        // advanced exactly once
        Subscription reloaded = subscriptionRepository.findById(subId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(reloaded.getEndAt()).isEqualTo(oldEnd.plus(30, ChronoUnit.DAYS)); // not +60
        assertThat(reloaded.getFailedChargeCount()).isZero();

        // one winner charged, one loser skipped on the UNIQUE key
        assertThat(List.of(o1, o2))
                .containsExactlyInAnyOrder(BillingOutcome.CHARGED_SUCCESS, BillingOutcome.SKIPPED_ALREADY_PROCESSED);
    }
}
