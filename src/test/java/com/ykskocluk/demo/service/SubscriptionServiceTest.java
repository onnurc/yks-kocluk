package com.ykskocluk.demo.service;

import com.ykskocluk.demo.config.PaymentProperties;
import com.ykskocluk.demo.dto.SubscriptionCheckoutResponse;
import com.ykskocluk.demo.dto.SubscriptionCreateRequest;
import com.ykskocluk.demo.dto.SubscriptionResponse;
import com.ykskocluk.demo.dto.IyzicoWebhookRequest;
import com.ykskocluk.demo.dto.IyzicoWebhookResponse;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.Package;
import com.ykskocluk.demo.entity.Payment;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.PaymentStatus;
import com.ykskocluk.demo.enums.PaymentType;
import com.ykskocluk.demo.enums.SubscriptionStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.integration.CheckoutResult;
import com.ykskocluk.demo.integration.IyzicoClient;
import com.ykskocluk.demo.mapper.SubscriptionMapper;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.PackageRepository;
import com.ykskocluk.demo.repository.PaymentRepository;
import com.ykskocluk.demo.repository.SubscriptionRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock SubscriptionRepository subscriptionRepository;
    @Mock PackageRepository packageRepository;
    @Mock CoachProfileRepository coachProfileRepository;
    @Mock UserRepository userRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock IyzicoClient iyzicoClient;
    @Mock SubscriptionMapper subscriptionMapper;

    PaymentProperties paymentProperties;
    SubscriptionService service;

    private static final long COACH_ID = 7L;
    private static final long PKG_ID = 3L;
    private static final long STUDENT_ID = 1L;

    @BeforeEach
    void setUp() {
        paymentProperties = new PaymentProperties(new BigDecimal("0.2000"), 3);
        service = new SubscriptionService(subscriptionRepository, packageRepository,
            coachProfileRepository, userRepository, paymentRepository, paymentProperties,
            iyzicoClient, subscriptionMapper);

        Package pkg = new Package();
        ReflectionTestUtils.setField(pkg, "id", PKG_ID);
        pkg.setActive(true);
        pkg.setDurationDays(30);
        pkg.setWeeklySessions(1);
        pkg.setPrice(BigDecimal.TEN);
        lenient().when(packageRepository.findById(PKG_ID)).thenReturn(Optional.of(pkg));

        CoachProfile coach = new CoachProfile();
        ReflectionTestUtils.setField(coach, "id", COACH_ID);
        coach.setStatus(CoachProfileStatus.APPROVED);
        lenient().when(coachProfileRepository.findByIdAndStatus(COACH_ID, CoachProfileStatus.APPROVED))
                .thenReturn(Optional.of(coach));

        lenient().when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(new User()));
    lenient().when(subscriptionRepository.findLiveSubscription(STUDENT_ID, COACH_ID))
        .thenReturn(Optional.empty());
    lenient().when(subscriptionRepository.existsByStudentIdAndCoachProfileIdAndStatus(
        STUDENT_ID, COACH_ID, SubscriptionStatus.PENDING_PAYMENT)).thenReturn(false);
    lenient().when(subscriptionMapper.toResponse(any())).thenAnswer(invocation -> {
        Subscription subscription = invocation.getArgument(0);
        return new SubscriptionResponse(1L, COACH_ID, "Coach", PKG_ID, "Aylık 1x", 1,
            subscription.getStatus(), subscription.getStartAt(), subscription.getEndAt());
    });
    lenient().when(iyzicoClient.initializeCheckout(any(), any(), any(), any()))
        .thenReturn(new CheckoutResult("stub-checkout-token", "https://checkout.stub.local/pay/stub-checkout-token"));
    }

    private SubscriptionCreateRequest request() {
        return new SubscriptionCreateRequest(COACH_ID, PKG_ID);
    }

    @Test
    void subscribe_success_createsPendingPayment_andDoesNotIncrementCapacity() {
        when(subscriptionRepository.findLiveSubscription(STUDENT_ID, COACH_ID)).thenReturn(Optional.empty());
        when(subscriptionRepository.existsByStudentIdAndCoachProfileIdAndStatus(
                STUDENT_ID, COACH_ID, SubscriptionStatus.PENDING_PAYMENT)).thenReturn(false);
        when(subscriptionRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionResponse response = service.subscribe(STUDENT_ID, request());

        assertThat(response.status()).isEqualTo(SubscriptionStatus.PENDING_PAYMENT);
        verify(coachProfileRepository, never()).incrementActiveStudentCountIfRoom(COACH_ID);
        verify(subscriptionRepository).saveAndFlush(any(Subscription.class));
    }

    @Test
    void subscribe_existingLiveSubscription_throwsConflict_noCapacityChange() {
        when(subscriptionRepository.findLiveSubscription(STUDENT_ID, COACH_ID))
                .thenReturn(Optional.of(new Subscription()));

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.subscribe(STUDENT_ID, request()));
        assertThat(ex.getErrorCode()).isEqualTo("ALREADY_SUBSCRIBED");
        verify(coachProfileRepository, never()).incrementActiveStudentCountIfRoom(COACH_ID);
        verify(subscriptionRepository, never()).saveAndFlush(any());
    }

    @Test
    void subscribe_existingPendingPayment_throwsConflict_noCapacityChange() {
        when(subscriptionRepository.existsByStudentIdAndCoachProfileIdAndStatus(
                STUDENT_ID, COACH_ID, SubscriptionStatus.PENDING_PAYMENT)).thenReturn(true);

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.subscribe(STUDENT_ID, request()));
        assertThat(ex.getErrorCode()).isEqualTo("ALREADY_SUBSCRIBED");
        verify(coachProfileRepository, never()).incrementActiveStudentCountIfRoom(eq(COACH_ID));
        verify(subscriptionRepository, never()).saveAndFlush(any());
    }

    @Test
    void subscribe_inactivePackage_throwsNotFound() {
        Package inactive = new Package();
        inactive.setActive(false);
        when(packageRepository.findById(PKG_ID)).thenReturn(Optional.of(inactive));

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.subscribe(STUDENT_ID, request()));
        assertThat(ex.getErrorCode()).isEqualTo("PACKAGE_NOT_FOUND");
    }

    @Test
    void subscribe_nonApprovedCoach_throwsNotFound() {
        when(coachProfileRepository.findByIdAndStatus(COACH_ID, CoachProfileStatus.APPROVED))
                .thenReturn(Optional.empty());

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.subscribe(STUDENT_ID, request()));
        assertThat(ex.getErrorCode()).isEqualTo("COACH_NOT_FOUND");
    }

    @Test
    void checkout_success_createsPendingPayment_andReturnsInitializationInfo() {
        when(subscriptionRepository.findLiveSubscription(STUDENT_ID, COACH_ID)).thenReturn(Optional.empty());
        when(subscriptionRepository.existsByStudentIdAndCoachProfileIdAndStatus(
                STUDENT_ID, COACH_ID, SubscriptionStatus.PENDING_PAYMENT)).thenReturn(false);
        when(subscriptionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Subscription subscription = invocation.getArgument(0);
            ReflectionTestUtils.setField(subscription, "id", 11L);
            return subscription;
        });
        when(paymentRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            ReflectionTestUtils.setField(payment, "id", 22L);
            return payment;
        });

        SubscriptionCheckoutResponse response = service.checkout(STUDENT_ID, request());

        assertThat(response.subscriptionId()).isEqualTo(11L);
        assertThat(response.paymentId()).isEqualTo(22L);
        assertThat(response.subscriptionStatus()).isEqualTo(SubscriptionStatus.PENDING_PAYMENT);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.checkoutToken()).isEqualTo("stub-checkout-token");
        verify(coachProfileRepository, never()).incrementActiveStudentCountIfRoom(COACH_ID);
        verify(paymentRepository).saveAndFlush(any(Payment.class));
    }

    @Test
    void succeedPayment_success_marksActiveAndSucceedsPayment_incrementsCapacity() {
        Subscription sub = new Subscription();
        sub.setStatus(SubscriptionStatus.PENDING_PAYMENT);
        Package pkg = new Package();
        pkg.setDurationDays(30);
        pkg.setWeeklySessions(1);
        pkg.setName("Aylık 1x");
        sub.setPkg(pkg);
        User student = new User();
        ReflectionTestUtils.setField(student, "id", STUDENT_ID);
        sub.setStudent(student);
        CoachProfile coach = new CoachProfile();
        ReflectionTestUtils.setField(coach, "id", COACH_ID);
        sub.setCoachProfile(coach);

        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", 100L);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setSubscription(sub);

        when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));
        when(coachProfileRepository.incrementActiveStudentCountIfRoom(COACH_ID)).thenReturn(1);

        SubscriptionResponse response = service.succeedPayment(100L, STUDENT_ID);

        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getProviderReference()).startsWith("stub-provider-ref-");
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.getStartAt()).isNotNull();
        assertThat(sub.getEndAt()).isNotNull();

        verify(coachProfileRepository).incrementActiveStudentCountIfRoom(COACH_ID);
        verify(paymentRepository).saveAndFlush(payment);
        verify(subscriptionRepository).saveAndFlush(sub);
    }

    @Test
    void succeedPayment_alreadySuccess_returnsImmediately_noCapacityIncrement() {
        Subscription sub = new Subscription();
        sub.setStatus(SubscriptionStatus.ACTIVE);
        User student = new User();
        ReflectionTestUtils.setField(student, "id", STUDENT_ID);
        sub.setStudent(student);

        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", 100L);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setSubscription(sub);

        when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));

        SubscriptionResponse response = service.succeedPayment(100L, STUDENT_ID);

        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(coachProfileRepository, never()).incrementActiveStudentCountIfRoom(any());
        verify(paymentRepository, never()).saveAndFlush(any());
        verify(subscriptionRepository, never()).saveAndFlush(any());
    }

    @Test
    void succeedPayment_unauthorizedStudent_throwsForbidden() {
        Subscription sub = new Subscription();
        User student = new User();
        ReflectionTestUtils.setField(student, "id", 999L);
        sub.setStudent(student);

        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", 100L);
        payment.setSubscription(sub);

        when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.succeedPayment(100L, STUDENT_ID));
        assertThat(ex.getErrorCode()).isEqualTo("NOT_PAYMENT_OWNER");
        assertThat(ex.getStatus()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
    }

    @Test
    void succeedPayment_failedPaymentStatus_throwsBadRequest() {
        Subscription sub = new Subscription();
        User student = new User();
        ReflectionTestUtils.setField(student, "id", STUDENT_ID);
        sub.setStudent(student);

        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", 100L);
        payment.setStatus(PaymentStatus.FAILED);
        payment.setSubscription(sub);

        when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.succeedPayment(100L, STUDENT_ID));
        assertThat(ex.getErrorCode()).isEqualTo("INVALID_PAYMENT_STATUS");
        assertThat(ex.getStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void succeedPayment_subscriptionNotPendingPayment_throwsBadRequest() {
        Subscription sub = new Subscription();
        sub.setStatus(SubscriptionStatus.ACTIVE);
        User student = new User();
        ReflectionTestUtils.setField(student, "id", STUDENT_ID);
        sub.setStudent(student);

        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", 100L);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setSubscription(sub);

        when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.succeedPayment(100L, STUDENT_ID));
        assertThat(ex.getErrorCode()).isEqualTo("INVALID_SUBSCRIPTION_STATUS");
        assertThat(ex.getStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void succeedPayment_coachFull_throwsConflict_rollsBack() {
        Subscription sub = new Subscription();
        sub.setStatus(SubscriptionStatus.PENDING_PAYMENT);
        User student = new User();
        ReflectionTestUtils.setField(student, "id", STUDENT_ID);
        sub.setStudent(student);
        CoachProfile coach = new CoachProfile();
        ReflectionTestUtils.setField(coach, "id", COACH_ID);
        sub.setCoachProfile(coach);

        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", 100L);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setSubscription(sub);

        when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));
        when(coachProfileRepository.incrementActiveStudentCountIfRoom(COACH_ID)).thenReturn(0);

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.succeedPayment(100L, STUDENT_ID));
        assertThat(ex.getErrorCode()).isEqualTo("COACH_FULL");
        assertThat(ex.getStatus()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);

        verify(coachProfileRepository).incrementActiveStudentCountIfRoom(COACH_ID);
        verify(paymentRepository, never()).saveAndFlush(any());
        verify(subscriptionRepository, never()).saveAndFlush(any());
    }

    @Test
    void processWebhook_success_activatesSubscriptionAndMarksPaymentSuccess() {
        Subscription sub = new Subscription();
        sub.setStatus(SubscriptionStatus.PENDING_PAYMENT);
        Package pkg = new Package();
        pkg.setDurationDays(30);
        pkg.setWeeklySessions(1);
        pkg.setName("Aylık 1x");
        sub.setPkg(pkg);
        CoachProfile coach = new CoachProfile();
        ReflectionTestUtils.setField(coach, "id", COACH_ID);
        sub.setCoachProfile(coach);

        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", 100L);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setSubscription(sub);

        when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));
        when(coachProfileRepository.incrementActiveStudentCountIfRoom(COACH_ID)).thenReturn(1);

        IyzicoWebhookRequest request = new IyzicoWebhookRequest(100L, "SUCCESS", "prov-ref-123");
        IyzicoWebhookResponse response = service.processWebhook(request);

        assertThat(response.status()).isEqualTo("PROCESSED");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getProviderReference()).isEqualTo("prov-ref-123");
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.getStartAt()).isNotNull();
        assertThat(sub.getEndAt()).isNotNull();

        verify(coachProfileRepository).incrementActiveStudentCountIfRoom(COACH_ID);
        verify(paymentRepository).saveAndFlush(payment);
        verify(subscriptionRepository).saveAndFlush(sub);
    }

    @Test
    void processWebhook_success_alreadySuccess_returnsIdempotent() {
        Subscription sub = new Subscription();
        sub.setStatus(SubscriptionStatus.ACTIVE);

        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", 100L);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setSubscription(sub);

        when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));

        IyzicoWebhookRequest request = new IyzicoWebhookRequest(100L, "SUCCESS", "prov-ref-123");
        IyzicoWebhookResponse response = service.processWebhook(request);

        assertThat(response.status()).isEqualTo("IDEMPOTENT");
        verify(coachProfileRepository, never()).incrementActiveStudentCountIfRoom(any());
        verify(paymentRepository, never()).saveAndFlush(any());
        verify(subscriptionRepository, never()).saveAndFlush(any());
    }

    @Test
    void processWebhook_failure_marksPaymentFailedAndKeepsSubscriptionPending() {
        Subscription sub = new Subscription();
        sub.setStatus(SubscriptionStatus.PENDING_PAYMENT);

        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", 100L);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setSubscription(sub);

        when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));

        IyzicoWebhookRequest request = new IyzicoWebhookRequest(100L, "FAILURE", null);
        IyzicoWebhookResponse response = service.processWebhook(request);

        assertThat(response.status()).isEqualTo("PROCESSED");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.PENDING_PAYMENT);

        verify(coachProfileRepository, never()).incrementActiveStudentCountIfRoom(any());
        verify(paymentRepository).saveAndFlush(payment);
        verify(subscriptionRepository, never()).saveAndFlush(any());
    }

    @Test
    void processWebhook_failure_alreadyFailed_returnsIdempotent() {
        Subscription sub = new Subscription();
        sub.setStatus(SubscriptionStatus.PENDING_PAYMENT);

        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", 100L);
        payment.setStatus(PaymentStatus.FAILED);
        payment.setSubscription(sub);

        when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));

        IyzicoWebhookRequest request = new IyzicoWebhookRequest(100L, "FAILURE", null);
        IyzicoWebhookResponse response = service.processWebhook(request);

        assertThat(response.status()).isEqualTo("IDEMPOTENT");
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void processWebhook_invalidStatus_throwsBadRequest() {
        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", 100L);

        when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));

        IyzicoWebhookRequest request = new IyzicoWebhookRequest(100L, "INVALID_STATUS", null);
        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.processWebhook(request));

        assertThat(ex.getErrorCode()).isEqualTo("UNSUPPORTED_WEBHOOK_STATUS");
        assertThat(ex.getStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void processWebhook_notFoundPayment_throwsNotFound() {
        when(paymentRepository.findById(100L)).thenReturn(Optional.empty());

        IyzicoWebhookRequest request = new IyzicoWebhookRequest(100L, "SUCCESS", null);
        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.processWebhook(request));

        assertThat(ex.getErrorCode()).isEqualTo("PAYMENT_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }
}
