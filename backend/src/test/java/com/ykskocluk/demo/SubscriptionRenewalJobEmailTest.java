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
import com.ykskocluk.demo.integration.ChargeResult;
import com.ykskocluk.demo.integration.IyzicoClient;
import com.ykskocluk.demo.integration.MailClient;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.PackageRepository;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 8d — the renewal job dispatches the right email per billing outcome (after processDue's tx2
 * commits), and the dispatch is best-effort: a mail throw never affects the committed billing state.
 * Emails are verified by the subscription's unique student email, so sibling-test rows in the shared
 * DB can't perturb the assertions.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SubscriptionRenewalJobEmailTest {

    private static final Instant NOW = Instant.parse("2026-07-01T09:00:00Z");

    @Autowired SubscriptionRenewalJob job;
    @Autowired UserRepository userRepository;
    @Autowired UniversityRepository universityRepository;
    @Autowired CoachProfileRepository coachProfileRepository;
    @Autowired PackageRepository packageRepository;
    @Autowired SubscriptionRepository subscriptionRepository;

    @MockitoBean IyzicoClient iyzicoClient;
    @MockitoBean MailClient mailClient;

    private CoachProfile coach() {
        University uni = new University();
        uni.setName("Uni " + System.nanoTime());
        universityRepository.save(uni);
        User u = new User();
        u.setEmail("coach-" + System.nanoTime() + "@example.com");
        u.setFullName("Coach Name");
        u.setRole(Role.COACH);
        u.setStatus(UserStatus.ACTIVE);
        userRepository.save(u);
        CoachProfile c = new CoachProfile();
        c.setUser(u);
        c.setUniversity(uni);
        c.setHeadline("Koç");
        c.setStatus(CoachProfileStatus.APPROVED);
        c.setMaxStudentCapacity(20);
        c.setActiveStudentCount(5);
        return coachProfileRepository.save(c);
    }

    /** Returns the seeded subscription's unique student email (the assertion key). */
    private Subscription sub(CoachProfile coach, SubscriptionStatus status, Instant endAt, int failedCount) {
        User student = new User();
        student.setEmail("stu-" + System.nanoTime() + "@example.com");
        student.setFullName("Student");
        student.setRole(Role.STUDENT);
        student.setStatus(UserStatus.ACTIVE);
        userRepository.save(student);
        Package pkg = packageRepository.findByActiveTrueOrderByPriceAsc().get(0);
        Subscription s = new Subscription();
        s.setStudent(student);
        s.setCoachProfile(coach);
        s.setPkg(pkg);
        s.setStatus(status);
        s.setStartAt(NOW.minus(40, ChronoUnit.DAYS));
        s.setEndAt(endAt);
        s.setFailedChargeCount(failedCount);
        s.setAutoRenew(true);
        s.setSavedCardToken("stub-card-token-x");
        return subscriptionRepository.save(s);
    }

    @Test
    void renewalSuccess_sendsRenewalSucceeded() {
        when(iyzicoClient.charge(any(), any(), any())).thenReturn(new ChargeResult(true, "ref"));
        Subscription s = sub(coach(), SubscriptionStatus.ACTIVE, NOW.minus(1, ChronoUnit.HOURS), 0);
        String email = s.getStudent().getEmail();

        job.runRenewals(NOW);

        verify(mailClient, times(1))
                .sendRenewalSucceeded(eq(email), eq("Coach Name"), any(), any());
    }

    @Test
    void chargeFail_sendsPaymentFailed_withAttemptNumber() {
        when(iyzicoClient.charge(any(), any(), any())).thenReturn(new ChargeResult(false, null));
        Subscription s = sub(coach(), SubscriptionStatus.ACTIVE, NOW.minus(1, ChronoUnit.HOURS), 0); // fail #1
        String email = s.getStudent().getEmail();

        job.runRenewals(NOW);

        verify(mailClient, times(1)).sendPaymentFailed(eq(email), eq("Coach Name"), eq(1), eq(3));
    }

    @Test
    void retryExhaustion_sendsSubscriptionExpired() {
        when(iyzicoClient.charge(any(), any(), any())).thenReturn(new ChargeResult(false, null));
        Subscription s = sub(coach(), SubscriptionStatus.PAST_DUE, NOW.minus(2, ChronoUnit.DAYS), 2); // 3rd fail
        String email = s.getStudent().getEmail();

        job.runRenewals(NOW);

        verify(mailClient, times(1)).sendSubscriptionExpired(eq(email), eq("Coach Name"));
    }

    @Test
    void mailThrow_doesNotAffectCommittedBillingState() {
        when(iyzicoClient.charge(any(), any(), any())).thenReturn(new ChargeResult(true, "ref"));
        Subscription s = sub(coach(), SubscriptionStatus.ACTIVE, NOW.minus(1, ChronoUnit.HOURS), 0);
        Instant oldEnd = s.getEndAt();
        // Mail blows up for this renewal — the dispatch swallows it; billing must be untouched.
        doThrow(new RuntimeException("resend down"))
                .when(mailClient).sendRenewalSucceeded(eq(s.getStudent().getEmail()), any(), any(), any());

        job.runRenewals(NOW); // must not throw

        Subscription reloaded = subscriptionRepository.findById(s.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE); // renewed
        assertThat(reloaded.getEndAt()).isEqualTo(oldEnd.plus(30, ChronoUnit.DAYS)); // advanced, committed
    }
}
