package com.ykskocluk.demo;

import com.ykskocluk.demo.dto.IyzicoWebhookRequest;
import com.ykskocluk.demo.dto.SubscriptionCheckoutResponse;
import com.ykskocluk.demo.dto.SubscriptionCreateRequest;
import com.ykskocluk.demo.dto.SubscriptionCheckoutRequest;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.Payment;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.entity.University;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.PaymentStatus;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.SubscriptionStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.PackageRepository;
import com.ykskocluk.demo.repository.PaymentRepository;
import com.ykskocluk.demo.repository.SubscriptionRepository;
import com.ykskocluk.demo.repository.UniversityRepository;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.service.SubscriptionBillingService;
import com.ykskocluk.demo.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * A subscription stuck in PENDING_PAYMENT (abandoned checkout, or a FAILURE webhook) must not
 * block the student forever. {@code expireStalePendingCheckouts} EXPIRES it past the configured
 * timeout, fails its PENDING charge, and frees the student to check out with that coach again.
 * The timeout is respected: a fresh PENDING_PAYMENT is left untouched.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PendingCheckoutExpiryTest {

    @Autowired private SubscriptionService subscriptionService;
    @Autowired private SubscriptionBillingService billingService;
    @Autowired private UserRepository userRepository;
    @Autowired private UniversityRepository universityRepository;
    @Autowired private CoachProfileRepository coachProfileRepository;
    @Autowired private PackageRepository packageRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private PaymentRepository paymentRepository;

    @Test
    void abandonedCheckout_freshIsKept_thenExpiresPastTimeout_allowsRecheckout() {
        Fixture f = fixture();

        SubscriptionCheckoutResponse checkout = subscriptionService.checkout(
                f.studentId, checkoutRequest(f.coachId, f.packageId));
        assertThat(subscriptionRepository.findById(checkout.subscriptionId()).orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.PENDING_PAYMENT);

        // Timeout respected: a fresh PENDING_PAYMENT (created just now) is NOT expired.
        assertThat(billingService.expireStalePendingCheckouts(Instant.now())).isZero();
        assertThat(subscriptionRepository.findById(checkout.subscriptionId()).orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.PENDING_PAYMENT);

        // While still PENDING_PAYMENT, the student is blocked from re-subscribing to this coach.
        ApiException blocked = catchThrowableOfType(ApiException.class, () ->
                subscriptionService.checkout(f.studentId, checkoutRequest(f.coachId, f.packageId)));
        assertThat(blocked.getErrorCode()).isEqualTo("ALREADY_SUBSCRIBED");

        // Past the timeout the stuck checkout is expired and its PENDING charge failed.
        int expired = billingService.expireStalePendingCheckouts(Instant.now().plus(2, ChronoUnit.HOURS));
        assertThat(expired).isGreaterThanOrEqualTo(1);

        Subscription stale = subscriptionRepository.findById(checkout.subscriptionId()).orElseThrow();
        assertThat(stale.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(paymentRepository.findById(checkout.paymentId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.FAILED);

        // The student can now check out with the same coach again — a brand-new subscription.
        SubscriptionCheckoutResponse retry = subscriptionService.checkout(
                f.studentId, checkoutRequest(f.coachId, f.packageId));
        assertThat(retry.subscriptionId()).isNotEqualTo(checkout.subscriptionId());
        assertThat(subscriptionRepository.findById(retry.subscriptionId()).orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.PENDING_PAYMENT);
    }

    @Test
    void failedPaymentWebhook_leavesPending_thenExpires_allowsRecheckout() {
        Fixture f = fixture();

        SubscriptionCheckoutResponse checkout = subscriptionService.checkout(
                f.studentId, checkoutRequest(f.coachId, f.packageId));

        // A FAILURE webhook fails the payment but leaves the subscription PENDING_PAYMENT.
        subscriptionService.processWebhook(new IyzicoWebhookRequest(checkout.paymentId(), "FAILURE", "ref-fail"));
        assertThat(paymentRepository.findById(checkout.paymentId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.FAILED);
        assertThat(subscriptionRepository.findById(checkout.subscriptionId()).orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.PENDING_PAYMENT);

        // Cleanup expires it past the timeout, unblocking a fresh checkout.
        assertThat(billingService.expireStalePendingCheckouts(Instant.now().plus(2, ChronoUnit.HOURS)))
                .isGreaterThanOrEqualTo(1);
        assertThat(subscriptionRepository.findById(checkout.subscriptionId()).orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.EXPIRED);

        SubscriptionCheckoutResponse retry = subscriptionService.checkout(
                f.studentId, checkoutRequest(f.coachId, f.packageId));
        assertThat(retry.subscriptionId()).isNotEqualTo(checkout.subscriptionId());
    }

    private record Fixture(Long studentId, Long coachId, Long packageId) {
    }

    private SubscriptionCheckoutRequest checkoutRequest(Long coachId, Long packageId) {
        return new SubscriptionCheckoutRequest(coachId, packageId, 6L, 7L, 8L, true);
    }

    private Fixture fixture() {
        University uni = new University();
        uni.setName("Pending Uni " + UUID.randomUUID());
        universityRepository.save(uni);

        User coachUser = TestUsers.create(userRepository, Role.COACH,
                "coach-pending-" + UUID.randomUUID() + "@example.com", "Coach");

        CoachProfile coach = new CoachProfile();
        coach.setUser(coachUser);
        coach.setUniversity(uni);
        coach.setHeadline("Headline");
        coach.setStatus(CoachProfileStatus.APPROVED);
        coach.setMaxStudentCapacity(5);
        coach.setActiveStudentCount(0);
        coachProfileRepository.save(coach);

        User student = TestUsers.create(userRepository, Role.STUDENT,
                "student-pending-" + UUID.randomUUID() + "@example.com", "Student");

        Long packageId = packageRepository.findByActiveTrueOrderByPriceAsc().get(0).getId();
        return new Fixture(student.getId(), coach.getId(), packageId);
    }
}
