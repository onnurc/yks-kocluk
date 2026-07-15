package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.TestcontainersConfiguration;
import com.ykskocluk.demo.config.JpaAuditingConfig;
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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
class PaymentRepositoryTest {

    @Autowired UserRepository userRepository;
    @Autowired UniversityRepository universityRepository;
    @Autowired CoachProfileRepository coachProfileRepository;
    @Autowired PackageRepository packageRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired PaymentRepository paymentRepository;

    private Subscription persistSubscription() {
        User student = new User();
        student.setEmail("stu-" + System.nanoTime() + "@example.com");
        student.setFullName("Student");
        student.setRole(Role.STUDENT);
        student.setStatus(UserStatus.ACTIVE);
        userRepository.save(student);

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
        coachProfileRepository.save(coach);

        Package pkg = new Package();
        pkg.setName("Pkg " + System.nanoTime());
        pkg.setWeeklySessions(1);
        pkg.setDurationDays(30);
        pkg.setPrice(new BigDecimal("1500.00"));
        pkg.setActive(true);
        packageRepository.save(pkg);

        Subscription sub = new Subscription();
        sub.setStudent(student);
        sub.setCoachProfile(coach);
        sub.setPkg(pkg);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStartAt(Instant.now());
        sub.setEndAt(Instant.now().plus(30, ChronoUnit.DAYS));
        sub.setSavedCardToken("stub-card-token-x");
        return subscriptionRepository.save(sub);
    }

    private Payment chargeRow(Subscription sub, PaymentStatus status, String key) {
        Payment p = new Payment();
        p.setSubscription(sub);
        p.setType(PaymentType.CHARGE);
        p.setAmount(new BigDecimal("1500.00"));
        p.setStatus(status);
        p.setIdempotencyKey(key);
        p.setCommissionRate(new BigDecimal("0.2000"));
        p.setCommissionAmount(new BigDecimal("300.00"));
        p.setCoachPayoutAmount(new BigDecimal("1200.00"));
        return p;
    }

    @Test
    void persistsAndReadsBack_withCommissionSnapshot() {
        Subscription sub = persistSubscription();

        Payment saved = paymentRepository.save(chargeRow(sub, PaymentStatus.SUCCESS, "charge:1:2026-06-27"));

        Payment found = paymentRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getType()).isEqualTo(PaymentType.CHARGE);
        assertThat(found.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(found.getAmount()).isEqualByComparingTo("1500.00");
        assertThat(found.getCommissionAmount()).isEqualByComparingTo("300.00");
        assertThat(found.getCoachPayoutAmount()).isEqualByComparingTo("1200.00");
        assertThat(found.getCreatedAt()).isNotNull();         // BaseEntity auditing wired
        assertThat(found.getSourcePayment()).isNull();         // refund seam unused in Stage 1
        assertThat(paymentRepository.existsByIdempotencyKey("charge:1:2026-06-27")).isTrue();
    }

    @Test
    void duplicateIdempotencyKey_violatesUniqueConstraint() {
        Subscription sub = persistSubscription();
        paymentRepository.saveAndFlush(chargeRow(sub, PaymentStatus.PENDING, "charge:dup:2026-06-27"));

        assertThatThrownBy(() ->
                paymentRepository.saveAndFlush(chargeRow(sub, PaymentStatus.PENDING, "charge:dup:2026-06-27")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
