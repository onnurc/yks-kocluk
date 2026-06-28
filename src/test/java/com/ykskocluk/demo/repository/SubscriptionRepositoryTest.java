package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.TestcontainersConfiguration;
import com.ykskocluk.demo.config.JpaAuditingConfig;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.Package;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.entity.University;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.SubscriptionStatus;
import com.ykskocluk.demo.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
class SubscriptionRepositoryTest {

    @Autowired UserRepository userRepository;
    @Autowired UniversityRepository universityRepository;
    @Autowired CoachProfileRepository coachProfileRepository;
    @Autowired PackageRepository packageRepository;
    @Autowired SubscriptionRepository subscriptionRepository;

    private User persistStudent() {
        User u = new User();
        u.setEmail("stu-" + System.nanoTime() + "@example.com");
        u.setFullName("Student");
        u.setRole(Role.STUDENT);
        u.setStatus(UserStatus.ACTIVE);
        return userRepository.save(u);
    }

    private CoachProfile persistCoach() {
        User coachUser = new User();
        coachUser.setEmail("coach-" + System.nanoTime() + "@example.com");
        coachUser.setFullName("Coach");
        coachUser.setRole(Role.COACH);
        coachUser.setStatus(UserStatus.ACTIVE);
        userRepository.save(coachUser);
        University uni = new University();
        uni.setName("Uni " + System.nanoTime());
        universityRepository.save(uni);
        CoachProfile coach = new CoachProfile();
        coach.setUser(coachUser);
        coach.setUniversity(uni);
        coach.setHeadline("Koç");
        coach.setStatus(CoachProfileStatus.APPROVED);
        coach.setMaxStudentCapacity(10);
        coach.setActiveStudentCount(0);
        return coachProfileRepository.save(coach);
    }

    private Package persistPackage() {
        Package pkg = new Package();
        pkg.setName("Pkg " + System.nanoTime());
        pkg.setWeeklySessions(1);
        pkg.setDurationDays(30);
        pkg.setPrice(new BigDecimal("1500.00"));
        pkg.setActive(true);
        return packageRepository.save(pkg);
    }

    private Subscription sub(User student, CoachProfile coach, Package pkg, SubscriptionStatus status) {
        Subscription s = new Subscription();
        s.setStudent(student);
        s.setCoachProfile(coach);
        s.setPkg(pkg);
        s.setStatus(status);
        s.setStartAt(Instant.now());
        s.setEndAt(Instant.now().plus(30, ChronoUnit.DAYS));
        return s;
    }

    @Test
    void newSubscription_defaultsAutoRenewTrue_andZeroFailures() {
        Subscription saved = subscriptionRepository.save(
                sub(persistStudent(), persistCoach(), persistPackage(), SubscriptionStatus.ACTIVE));

        Subscription found = subscriptionRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.isAutoRenew()).isTrue();               // entity default
        assertThat(found.getFailedChargeCount()).isZero();
        assertThat(found.getCancelledAt()).isNull();
        assertThat(found.getLastChargeAttemptAt()).isNull();
    }

    @Test
    void liveSubscriptionGuard_pastDueBlocksDuplicate_ofActivePair() {
        User student = persistStudent();
        CoachProfile coach = persistCoach();
        Package pkg = persistPackage();
        subscriptionRepository.saveAndFlush(sub(student, coach, pkg, SubscriptionStatus.ACTIVE));

        // A second LIVE row (PAST_DUE) for the same (student, coach) must be rejected by the
        // widened partial-unique index (V10: ACTIVE + PAST_DUE).
        assertThatThrownBy(() ->
                subscriptionRepository.saveAndFlush(sub(student, coach, pkg, SubscriptionStatus.PAST_DUE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void liveSubscriptionGuard_expiredDoesNotBlock_reSubscribe() {
        User student = persistStudent();
        CoachProfile coach = persistCoach();
        Package pkg = persistPackage();
        subscriptionRepository.saveAndFlush(sub(student, coach, pkg, SubscriptionStatus.EXPIRED));

        // EXPIRED is not "live", so a new ACTIVE sub for the same pair is allowed (re-subscribe).
        assertThatCode(() ->
                subscriptionRepository.saveAndFlush(sub(student, coach, pkg, SubscriptionStatus.ACTIVE)))
                .doesNotThrowAnyException();
    }
}
