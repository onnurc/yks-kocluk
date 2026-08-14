package com.ykskocluk.demo;

import com.ykskocluk.demo.dto.SubscriptionCheckoutResponse;
import com.ykskocluk.demo.dto.SubscriptionCreateRequest;
import com.ykskocluk.demo.dto.SubscriptionCheckoutRequest;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.Payment;
import com.ykskocluk.demo.entity.University;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.PaymentStatus;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.PackageRepository;
import com.ykskocluk.demo.repository.PaymentRepository;
import com.ykskocluk.demo.repository.UniversityRepository;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the double-refund race: two concurrent full-amount refunds on the same charge must not
 * both succeed. tx1 in {@code reserveRefund} takes a pessimistic lock on the original payment
 * (serializing reservers), and the refundable-headroom check counts PENDING (in-flight) refunds
 * as well as SUCCESS ones — so the second reserver sees the first's reservation and is rejected
 * with {@code EXCEEDS_REFUNDABLE_AMOUNT}. Net refunded never exceeds the original amount.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RefundConcurrencyTest {

    @Autowired private SubscriptionService subscriptionService;
    @Autowired private UserRepository userRepository;
    @Autowired private UniversityRepository universityRepository;
    @Autowired private CoachProfileRepository coachProfileRepository;
    @Autowired private PackageRepository packageRepository;
    @Autowired private PaymentRepository paymentRepository;

    @Test
    void twoConcurrentFullRefunds_onlyOneSucceeds_noOverRefund() throws Exception {
        University uni = new University();
        uni.setName("Refund Uni " + UUID.randomUUID());
        universityRepository.save(uni);

        User coachUser = TestUsers.create(userRepository, Role.COACH,
                "coach-refund-" + UUID.randomUUID() + "@example.com", "Coach");

        CoachProfile coach = new CoachProfile();
        coach.setUser(coachUser);
        coach.setUniversity(uni);
        coach.setHeadline("Headline");
        coach.setStatus(CoachProfileStatus.APPROVED);
        coach.setMaxStudentCapacity(5);
        coach.setActiveStudentCount(0);
        coachProfileRepository.save(coach);

        User student = student("student-refund-" + UUID.randomUUID() + "@example.com");
        Long packageId = packageRepository.findByActiveTrueOrderByPriceAsc().get(0).getId();

        // A SUCCESS charge to refund against.
        SubscriptionCheckoutResponse checkout = subscriptionService.checkout(
                student.getId(), new SubscriptionCheckoutRequest(
                        coach.getId(), packageId, 6L, 7L, 8L, true));
        subscriptionService.succeedPayment(checkout.paymentId(), student.getId());

        Long chargeId = checkout.paymentId();
        BigDecimal fullAmount = paymentRepository.findById(chargeId).orElseThrow().getAmount();

        // Both threads request the FULL amount — at most one can legitimately go through.
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> attempt = () -> {
            start.await();
            try {
                subscriptionService.refund(chargeId, fullAmount, "concurrent");
                return "OK";
            } catch (OptimisticLockingFailureException e) {
                return "CONCURRENT_UPDATE";
            } catch (ApiException e) {
                return e.getErrorCode();
            }
        };

        Future<String> f1 = pool.submit(attempt);
        Future<String> f2 = pool.submit(attempt);
        start.countDown();

        String r1 = f1.get(15, TimeUnit.SECONDS);
        String r2 = f2.get(15, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(List.of(r1, r2)).containsExactlyInAnyOrder("OK", "EXCEEDS_REFUNDABLE_AMOUNT");

        // Exactly one SUCCESS refund, and total refunded equals the original amount (never doubled).
        List<Payment> successRefunds = paymentRepository.findBySourcePaymentIdAndStatus(chargeId, PaymentStatus.SUCCESS);
        assertThat(successRefunds).hasSize(1);
        BigDecimal totalRefunded = successRefunds.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalRefunded).isEqualByComparingTo(fullAmount);
    }

    private User student(String email) {
        return TestUsers.create(userRepository, Role.STUDENT, email, "Student");
    }
}
