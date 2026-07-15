package com.ykskocluk.demo;

import com.ykskocluk.demo.dto.SubscriptionCheckoutResponse;
import com.ykskocluk.demo.dto.SubscriptionCreateRequest;
import com.ykskocluk.demo.dto.IyzicoWebhookRequest;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.Payment;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.entity.University;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.PaymentStatus;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.SubscriptionStatus;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.PackageRepository;
import com.ykskocluk.demo.repository.PaymentRepository;
import com.ykskocluk.demo.repository.SubscriptionRepository;
import com.ykskocluk.demo.repository.UniversityRepository;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SubscriptionCapacityConcurrencyTest {

    @Autowired private SubscriptionService subscriptionService;
    @Autowired private UserRepository userRepository;
    @Autowired private UniversityRepository universityRepository;
    @Autowired private CoachProfileRepository coachProfileRepository;
    @Autowired private PackageRepository packageRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private User student(String email) {
        User u = new User();
        u.setEmail(email);
        u.setFullName("Student");
        u.setRole(Role.STUDENT);
        u.setStatus(UserStatus.ACTIVE);
        return userRepository.save(u);
    }

    @Test
    void twoStudentsOneSeat_exactlyOnePaymentSucceeds() throws Exception {
        University uni = new University();
        uni.setName("Capacity Uni " + UUID.randomUUID());
        universityRepository.save(uni);

        User coachUser = new User();
        coachUser.setEmail("coach-cap-" + UUID.randomUUID() + "@example.com");
        coachUser.setFullName("Coach");
        coachUser.setRole(Role.COACH);
        coachUser.setStatus(UserStatus.ACTIVE);
        userRepository.save(coachUser);

        CoachProfile coach = new CoachProfile();
        coach.setUser(coachUser);
        coach.setUniversity(uni);
        coach.setHeadline("Headline");
        coach.setStatus(CoachProfileStatus.APPROVED);
        coach.setMaxStudentCapacity(1); // exactly one seat
        coach.setActiveStudentCount(0);
        coachProfileRepository.save(coach);

        Long coachId = coach.getId();
        User studentA = student("student-a-" + UUID.randomUUID() + "@example.com");
        User studentB = student("student-b-" + UUID.randomUUID() + "@example.com");
        Long packageId = packageRepository.findByActiveTrueOrderByPriceAsc().get(0).getId();

        // 1. Both checkout (which is fine, checkout doesn't check/increment capacity)
        SubscriptionCheckoutResponse checkoutA = subscriptionService.checkout(studentA.getId(), new SubscriptionCreateRequest(coachId, packageId));
        SubscriptionCheckoutResponse checkoutB = subscriptionService.checkout(studentB.getId(), new SubscriptionCreateRequest(coachId, packageId));

        // 2. Both try to concurrently succeed payment
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<String> attemptA = () -> {
            start.await();
            try {
                subscriptionService.succeedPayment(checkoutA.paymentId(), studentA.getId());
                return "OK";
            } catch (ApiException e) {
                return e.getErrorCode();
            }
        };

        Callable<String> attemptB = () -> {
            start.await();
            try {
                subscriptionService.succeedPayment(checkoutB.paymentId(), studentB.getId());
                return "OK";
            } catch (ApiException e) {
                return e.getErrorCode();
            }
        };

        Future<String> fa = pool.submit(attemptA);
        Future<String> fb = pool.submit(attemptB);
        start.countDown(); // fire both at once

        String ra = fa.get(15, TimeUnit.SECONDS);
        String rb = fb.get(15, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(List.of(ra, rb)).containsExactlyInAnyOrder("OK", "COACH_FULL");

        CoachProfile reloaded = coachProfileRepository.findById(coachId).orElseThrow();
        assertThat(reloaded.getActiveStudentCount()).isEqualTo(1); // never over-incremented

        Subscription subA = subscriptionRepository.findById(checkoutA.subscriptionId()).orElseThrow();
        Subscription subB = subscriptionRepository.findById(checkoutB.subscriptionId()).orElseThrow();

        // Exactly one of the subscriptions is ACTIVE
        assertThat(List.of(subA.getStatus(), subB.getStatus())).containsExactlyInAnyOrder(SubscriptionStatus.ACTIVE, SubscriptionStatus.PENDING_PAYMENT);
    }

    @Test
    void concurrentSucceedPayment_samePaymentId_onlyOneSucceeds() throws Exception {
        University uni = new University();
        uni.setName("SamePay Uni " + UUID.randomUUID());
        universityRepository.save(uni);

        User coachUser = new User();
        coachUser.setEmail("coach-same-" + UUID.randomUUID() + "@example.com");
        coachUser.setFullName("Coach");
        coachUser.setRole(Role.COACH);
        coachUser.setStatus(UserStatus.ACTIVE);
        userRepository.save(coachUser);

        CoachProfile coach = new CoachProfile();
        coach.setUser(coachUser);
        coach.setUniversity(uni);
        coach.setHeadline("Headline");
        coach.setStatus(CoachProfileStatus.APPROVED);
        coach.setMaxStudentCapacity(5);
        coach.setActiveStudentCount(0);
        coachProfileRepository.save(coach);

        Long coachId = coach.getId();
        User student = student("student-same-" + UUID.randomUUID() + "@example.com");
        Long packageId = packageRepository.findByActiveTrueOrderByPriceAsc().get(0).getId();

        SubscriptionCheckoutResponse checkout = subscriptionService.checkout(student.getId(), new SubscriptionCreateRequest(coachId, packageId));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<String> attempt = () -> {
            start.await();
            try {
                subscriptionService.succeedPayment(checkout.paymentId(), student.getId());
                return "OK";
            } catch (org.springframework.dao.OptimisticLockingFailureException e) {
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

        CoachProfile reloaded = coachProfileRepository.findById(coachId).orElseThrow();
        assertThat(reloaded.getActiveStudentCount()).isEqualTo(1); // never doubled

        Payment payment = paymentRepository.findById(checkout.paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void concurrentWebhook_samePaymentId_onlyOneSucceeds() throws Exception {
        University uni = new University();
        uni.setName("WebhookSame Uni " + UUID.randomUUID());
        universityRepository.save(uni);

        User coachUser = new User();
        coachUser.setEmail("coach-web-same-" + UUID.randomUUID() + "@example.com");
        coachUser.setFullName("Coach");
        coachUser.setRole(Role.COACH);
        coachUser.setStatus(UserStatus.ACTIVE);
        userRepository.save(coachUser);

        CoachProfile coach = new CoachProfile();
        coach.setUser(coachUser);
        coach.setUniversity(uni);
        coach.setHeadline("Headline");
        coach.setStatus(CoachProfileStatus.APPROVED);
        coach.setMaxStudentCapacity(5);
        coach.setActiveStudentCount(0);
        coachProfileRepository.save(coach);

        Long coachId = coach.getId();
        User student = student("student-web-same-" + UUID.randomUUID() + "@example.com");
        Long packageId = packageRepository.findByActiveTrueOrderByPriceAsc().get(0).getId();

        SubscriptionCheckoutResponse checkout = subscriptionService.checkout(student.getId(), new SubscriptionCreateRequest(coachId, packageId));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        IyzicoWebhookRequest request = new IyzicoWebhookRequest(checkout.paymentId(), "SUCCESS", "ref-web-concurrency");

        Callable<String> attempt = () -> {
            start.await();
            try {
                subscriptionService.processWebhook(request);
                return "OK";
            } catch (org.springframework.dao.OptimisticLockingFailureException e) {
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

        CoachProfile reloaded = coachProfileRepository.findById(coachId).orElseThrow();
        assertThat(reloaded.getActiveStudentCount()).isEqualTo(1); // never doubled

        Payment payment = paymentRepository.findById(checkout.paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void whenTransactionRollsBack_capacityIncrementIsRolledBack() {
        University uni = new University();
        uni.setName("Rollback Uni " + UUID.randomUUID());
        universityRepository.save(uni);

        User coachUser = new User();
        coachUser.setEmail("coach-roll-" + UUID.randomUUID() + "@example.com");
        coachUser.setFullName("Coach");
        coachUser.setRole(Role.COACH);
        coachUser.setStatus(UserStatus.ACTIVE);
        userRepository.save(coachUser);

        CoachProfile coach = new CoachProfile();
        coach.setUser(coachUser);
        coach.setUniversity(uni);
        coach.setHeadline("Headline");
        coach.setStatus(CoachProfileStatus.APPROVED);
        coach.setMaxStudentCapacity(5);
        coach.setActiveStudentCount(0);
        coachProfileRepository.save(coach);

        Long coachId = coach.getId();

        try {
            transactionTemplate.execute(status -> {
                coachProfileRepository.incrementActiveStudentCountIfRoom(coachId);
                throw new RuntimeException("Simulated error to trigger rollback");
            });
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo("Simulated error to trigger rollback");
        }

        CoachProfile reloaded = coachProfileRepository.findById(coachId).orElseThrow();
        assertThat(reloaded.getActiveStudentCount()).isZero(); // successfully rolled back!
    }
}
