package com.ykskocluk.demo;

import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.Package;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.entity.University;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.SubscriptionStatus;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Overlapping renewal-job runs (Phase 8c): two job invocations process the SAME due set at the same
 * instant. The {@code UNIQUE(idempotency_key)} guard (proven per-subscription in 8b) holds at the JOB
 * level too — each due subscription charges EXACTLY ONCE and advances exactly once, no matter how many
 * runs overlap. The spy wraps the real {@code StubIyzicoClient} so charges actually execute and count.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SubscriptionRenewalJobConcurrencyTest {

    private static final Instant NOW = Instant.parse("2026-07-01T09:00:00Z");
    private static final int DUE_COUNT = 3;

    @Autowired SubscriptionRenewalJob job;
    @Autowired UserRepository userRepository;
    @Autowired UniversityRepository universityRepository;
    @Autowired CoachProfileRepository coachProfileRepository;
    @Autowired PackageRepository packageRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired PaymentRepository paymentRepository;

    @MockitoSpyBean IyzicoClient iyzicoClient; // wraps the real StubIyzicoClient (always succeeds)

    private User user(String prefix, Role role) {
        return TestUsers.create(userRepository, role, prefix + System.nanoTime() + "@example.com");
    }

    @Test
    void overlappingRuns_chargeEachDueSubscriptionExactlyOnce() throws Exception {
        University uni = new University();
        uni.setName("Job University " + System.nanoTime());
        universityRepository.save(uni);
        CoachProfile coach = new CoachProfile();
        coach.setUser(user("coach-job-", Role.COACH));
        coach.setUniversity(uni);
        coach.setHeadline("Koç");
        coach.setStatus(CoachProfileStatus.APPROVED);
        coach.setMaxStudentCapacity(50);
        coach.setActiveStudentCount(DUE_COUNT);
        coachProfileRepository.save(coach);

        Package pkg = packageRepository.findByActiveTrueOrderByPriceAsc().get(0);
        Instant oldEnd = NOW.minus(1, ChronoUnit.HOURS); // all due

        List<Long> subIds = new ArrayList<>();
        for (int i = 0; i < DUE_COUNT; i++) {
            Subscription s = new Subscription();
            s.setStudent(user("stu-job-", Role.STUDENT));
            s.setCoachProfile(coach);
            s.setPkg(pkg);
            s.setStatus(SubscriptionStatus.ACTIVE);
            s.setStartAt(NOW.minus(31, ChronoUnit.DAYS));
            s.setEndAt(oldEnd);
            s.setAutoRenew(true);
            s.setSavedCardToken("stub-card-token-x");
            subscriptionRepository.save(s);
            subIds.add(s.getId());
        }

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Runnable run = () -> {
            try {
                start.await();
                job.runRenewals(NOW);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        Future<?> f1 = pool.submit(run);
        Future<?> f2 = pool.submit(run);
        start.countDown(); // fire both at once

        f1.get(30, TimeUnit.SECONDS);
        f2.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        // Each due sub charged EXACTLY ONCE across BOTH overlapping runs (not twice). Verified
        // per-subscription by its idempotency key — robust against sibling-test rows in the shared DB.
        for (Long id : subIds) {
            verify(iyzicoClient, times(1)).charge(any(), any(), eq("charge:" + id + ":2026-07-01"));
            assertThat(paymentRepository.findBySubscriptionIdOrderByCreatedAtDesc(id)).hasSize(1);
            Subscription reloaded = subscriptionRepository.findById(id).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(reloaded.getEndAt()).isEqualTo(oldEnd.plus(30, ChronoUnit.DAYS)); // advanced once
        }
    }
}
