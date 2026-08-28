package com.ykskocluk.demo.service;

import com.ykskocluk.demo.config.PaymentProperties;
import com.ykskocluk.demo.dto.SubscriptionCheckoutResponse;
import com.ykskocluk.demo.dto.SubscriptionCheckoutRequest;
import com.ykskocluk.demo.dto.SubscriptionCreateRequest;
import com.ykskocluk.demo.dto.SubscriptionResponse;
import com.ykskocluk.demo.dto.IyzicoWebhookRequest;
import com.ykskocluk.demo.dto.IyzicoWebhookResponse;
import com.ykskocluk.demo.dto.AdminSubscriptionTerminateResponse;
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
import com.ykskocluk.demo.exception.PaymentProviderException;
import com.ykskocluk.demo.integration.CheckoutResult;
import com.ykskocluk.demo.integration.IyzicoClient;
import com.ykskocluk.demo.mapper.SubscriptionMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.ykskocluk.demo.dto.RefundResponse;
import com.ykskocluk.demo.integration.RefundResult;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock SubscriptionRepository subscriptionRepository;
    @Mock PackageRepository packageRepository;
    @Mock CoachProfileRepository coachProfileRepository;
    @Mock UserRepository userRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock IyzicoClient iyzicoClient;
    @Mock SubscriptionMapper subscriptionMapper;
    @Mock EntityManager entityManager;
    @Mock AccountReadinessService accountReadinessService;
    @Mock LegalAcceptanceService legalAcceptanceService;
    @Mock org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Mock org.springframework.context.ApplicationEventPublisher events;

    PaymentProperties paymentProperties;
    SubscriptionService service;

    private static final long COACH_ID = 7L;
    private static final long PKG_ID = 3L;
    private static final long STUDENT_ID = 1L;

    @BeforeEach
    void setUp() {
        paymentProperties = new PaymentProperties(new BigDecimal("0.2000"), 3, 30);
        service = new SubscriptionService(subscriptionRepository, packageRepository,
            coachProfileRepository, userRepository, paymentRepository, paymentProperties,
            iyzicoClient, subscriptionMapper, entityManager,
            new com.ykskocluk.demo.config.IyzicoProperties(false, "sandbox", "dummy", "dummy", "dummy", "dummy"),
            accountReadinessService, legalAcceptanceService, transactionManager, events);

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
            subscription.getStatus(), subscription.isAutoRenew(), subscription.getStartAt(), subscription.getEndAt());
    });
    lenient().when(iyzicoClient.initializeCheckout(any(), any(), any(), any()))
        .thenReturn(new CheckoutResult("stub-checkout-token",
                "http://localhost:5173/payment/stub/stub-checkout-22-123e4567-e89b-42d3-a456-426614174000"));
    }

    private SubscriptionCreateRequest request() {
        return new SubscriptionCreateRequest(COACH_ID, PKG_ID);
    }

    private SubscriptionCheckoutRequest checkoutRequest() {
        return new SubscriptionCheckoutRequest(COACH_ID, PKG_ID, 6L, 7L, 8L, true);
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
    void subscribe_minorStudentWithoutLegacyConsent_isNotSeparatelyBlocked() {
        User minor = new User();
        minor.setDateOfBirth(java.time.LocalDate.now().minusYears(16));
        when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(minor));
        when(subscriptionRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionResponse response = service.subscribe(STUDENT_ID, request());

        assertThat(response.status()).isEqualTo(SubscriptionStatus.PENDING_PAYMENT);
        verify(accountReadinessService).requireReady(minor);
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

        SubscriptionCheckoutResponse response = service.checkout(STUDENT_ID, checkoutRequest());

        assertThat(response.subscriptionId()).isEqualTo(11L);
        assertThat(response.paymentId()).isEqualTo(22L);
        assertThat(response.subscriptionStatus()).isEqualTo(SubscriptionStatus.PENDING_PAYMENT);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.checkoutToken()).isEqualTo("stub-checkout-token");
        verify(coachProfileRepository, never()).incrementActiveStudentCountIfRoom(COACH_ID);
        verify(paymentRepository).saveAndFlush(any(Payment.class));
        verify(legalAcceptanceService).validateCheckout(checkoutRequest());
        verify(legalAcceptanceService).recordCheckoutAcceptances(any(User.class), any(Subscription.class),
                any(Payment.class), any());
    }

    @Test
    void checkout_rejectsProviderRedirectOutsideTrustedHosts() {
        when(subscriptionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Subscription subscription = invocation.getArgument(0);
            ReflectionTestUtils.setField(subscription, "id", 11L);
            return subscription;
        });
        when(paymentRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            ReflectionTestUtils.setField(payment, "id", 12L);
            return payment;
        });
        when(iyzicoClient.initializeCheckout(any(), any(), any(), any()))
                .thenReturn(new CheckoutResult("token", "https://attacker.example/checkout"));

        assertThat(catchThrowableOfType(PaymentProviderException.class,
                () -> service.checkout(STUDENT_ID, checkoutRequest())))
                .hasMessageContaining("untrusted checkout URL");
    }

    @Test
    void checkout_legalValidationFailure_happensBeforePaymentProviderInitialization() {
        when(legalAcceptanceService.validateCheckout(any())).thenThrow(new ApiException(
                HttpStatus.BAD_REQUEST, "REQUIRED_CHECKOUT_LEGAL_ACCEPTANCE_MISSING", "Kabul gerekli"));

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.checkout(STUDENT_ID, checkoutRequest()));

        assertThat(ex.getErrorCode()).isEqualTo("REQUIRED_CHECKOUT_LEGAL_ACCEPTANCE_MISSING");
        verify(subscriptionRepository, never()).saveAndFlush(any());
        verify(paymentRepository, never()).saveAndFlush(any());
        verify(iyzicoClient, never()).initializeCheckout(any(), any(), any(), any());
    }

    @Test
    void checkout_existingPendingPayment_preservesDuplicateGuardAndCreatesNoEvidence() {
        when(subscriptionRepository.existsByStudentIdAndCoachProfileIdAndStatus(
                STUDENT_ID, COACH_ID, SubscriptionStatus.PENDING_PAYMENT)).thenReturn(true);

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.checkout(STUDENT_ID, checkoutRequest()));

        assertThat(ex.getErrorCode()).isEqualTo("ALREADY_SUBSCRIBED");
        verifyNoInteractions(legalAcceptanceService);
        verify(iyzicoClient, never()).initializeCheckout(any(), any(), any(), any());
    }

    @Test
    void checkout_incompleteRegistrationLegalOnboarding_remainsBlocked() {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "LEGAL_ONBOARDING_REQUIRED", "Onboarding gerekli"))
                .when(accountReadinessService).requireReady(any());

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.checkout(STUDENT_ID, checkoutRequest()));

        assertThat(ex.getErrorCode()).isEqualTo("LEGAL_ONBOARDING_REQUIRED");
        verifyNoInteractions(legalAcceptanceService);
        verify(iyzicoClient, never()).initializeCheckout(any(), any(), any(), any());
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
        User coachUser = new User(); coachUser.setFullName("Coach"); coach.setUser(coachUser);
        sub.setCoachProfile(coach);

        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", 100L);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setType(PaymentType.CHARGE);
        payment.setSubscription(sub);

        when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));
        when(coachProfileRepository.incrementActiveStudentCountIfRoom(COACH_ID)).thenReturn(1);

        SubscriptionResponse response = service.succeedPayment(100L, STUDENT_ID);

        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getProviderReference()).startsWith("stub-provider-ref-");
        assertThat(payment.getSucceededAt()).isNotNull();
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.getStartAt()).isNotNull();
        assertThat(sub.getEndAt()).isNotNull();

        verify(coachProfileRepository).incrementActiveStudentCountIfRoom(COACH_ID);
        verify(paymentRepository).saveAndFlush(payment);
        verify(subscriptionRepository).saveAndFlush(sub);
        verify(events).publishEvent(any(PurchaseConfirmedEvent.class));
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
        payment.setType(PaymentType.CHARGE);
        payment.setSubscription(sub);

        when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));

        SubscriptionResponse response = service.succeedPayment(100L, STUDENT_ID);

        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(coachProfileRepository, never()).incrementActiveStudentCountIfRoom(any());
        verify(events, never()).publishEvent(any(PurchaseConfirmedEvent.class));
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
        payment.setType(PaymentType.CHARGE);
        payment.setSubscription(sub);

        when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));

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
        payment.setType(PaymentType.CHARGE);
        payment.setSubscription(sub);

        when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));

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
        payment.setType(PaymentType.CHARGE);
        payment.setSubscription(sub);

        when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));

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
        payment.setType(PaymentType.CHARGE);
        payment.setSubscription(sub);

        when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(payment));
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
        User student = new User(); student.setEmail("student@example.com"); student.setFullName("Student"); sub.setStudent(student);
        Package pkg = new Package();
        pkg.setDurationDays(30);
        pkg.setWeeklySessions(1);
        pkg.setName("Aylık 1x");
        sub.setPkg(pkg);
        CoachProfile coach = new CoachProfile();
        ReflectionTestUtils.setField(coach, "id", COACH_ID);
        User coachUser = new User(); coachUser.setFullName("Coach"); coach.setUser(coachUser);
        sub.setCoachProfile(coach);

        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", 100L);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setType(PaymentType.CHARGE);
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
        payment.setType(PaymentType.CHARGE);
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
        payment.setType(PaymentType.CHARGE);
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
        payment.setType(PaymentType.CHARGE);
        payment.setSubscription(sub);

        when(paymentRepository.findById(100L)).thenReturn(Optional.of(payment));

        IyzicoWebhookRequest request = new IyzicoWebhookRequest(100L, "FAILURE", null);
        IyzicoWebhookResponse response = service.processWebhook(request);

        assertThat(response.status()).isEqualTo("IDEMPOTENT");
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void processWebhook_invalidStatus_throwsBadRequest() {
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

    @Test
    void httpWebhookPathFailsClosedWhenSignatureCannotBeVerified() {
        IyzicoWebhookRequest request = new IyzicoWebhookRequest(100L, "SUCCESS", null);

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.processWebhook(request, null));

        assertThat(ex.getErrorCode()).isEqualTo("WEBHOOK_DISABLED");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(paymentRepository);
    }

    // --- refund tests ---

    @Test
    void refund_success_fullRefund_createsRefundPaymentAndUpdatesOriginal() {
        Subscription sub = new Subscription();
        Payment original = new Payment();
        ReflectionTestUtils.setField(original, "id", 100L);
        original.setSubscription(sub);
        original.setType(PaymentType.CHARGE);
        original.setStatus(PaymentStatus.SUCCESS);
        original.setSucceededAt(Instant.now().minus(1, ChronoUnit.DAYS));
        original.setAmount(new BigDecimal("150.00"));
        original.setCommissionRate(new BigDecimal("0.2000"));
        original.setProviderReference("prov-ref-123");

        when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(original));
        when(paymentRepository.findBySourcePaymentIdAndStatusIn(100L, List.of(PaymentStatus.PENDING, PaymentStatus.SUCCESS)))
                .thenReturn(List.of());
        when(iyzicoClient.refund(eq("prov-ref-123"), eq(new BigDecimal("150.00")), any()))
                .thenReturn(new RefundResult(true, "refund-prov-ref-999", null, null));

        RefundResponse response = service.refund(100L, new BigDecimal("150.00"), "Full Refund Reason");

        assertThat(response).isNotNull();
        assertThat(response.originalPaymentId()).isEqualTo(100L);
        assertThat(response.refundStatus()).isEqualTo("SUCCESS");
        assertThat(response.amount()).isEqualByComparingTo("150.00");
        assertThat(response.remainingRefundableAmount()).isEqualByComparingTo("0.00");

        // Reserved once (PENDING, before the external call) and finalized once (SUCCESS, after) —
        // two writes to the same row, matching the tx1/external-call/tx2 boundary in CLAUDE.md.
        verify(paymentRepository, times(2)).saveAndFlush(argThat(p ->
                p.getType() == PaymentType.REFUND &&
                p.getAmount().compareTo(new BigDecimal("150.00")) == 0 &&
                p.getCommissionAmount().compareTo(new BigDecimal("30.00")) == 0 && // 150 * 0.20
                p.getCoachPayoutAmount().compareTo(new BigDecimal("120.00")) == 0 &&
                p.getSourcePayment() == original
        ));
        verify(entityManager).lock(original, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }

    @Test
    void refund_success_partialRefund_isAllowed() {
        Subscription sub = new Subscription();
        Payment original = new Payment();
        ReflectionTestUtils.setField(original, "id", 100L);
        original.setSubscription(sub);
        original.setType(PaymentType.CHARGE);
        original.setStatus(PaymentStatus.SUCCESS);
        original.setSucceededAt(Instant.now().minus(1, ChronoUnit.DAYS));
        original.setAmount(new BigDecimal("150.00"));
        original.setCommissionRate(new BigDecimal("0.2000"));
        original.setProviderReference("prov-ref-123");

        // Existing partial refund of 50.00
        Payment prevRefund = new Payment();
        prevRefund.setAmount(new BigDecimal("50.00"));
        prevRefund.setStatus(PaymentStatus.SUCCESS);

        when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(original));
        when(paymentRepository.findBySourcePaymentIdAndStatusIn(100L, List.of(PaymentStatus.PENDING, PaymentStatus.SUCCESS)))
                .thenReturn(List.of(prevRefund));
        // New refund request of 50.00 (remaining is 150.00 - 50.00 = 100.00)
        when(iyzicoClient.refund(eq("prov-ref-123"), eq(new BigDecimal("50.00")), any()))
                .thenReturn(new RefundResult(true, "refund-prov-ref-888", null, null));

        RefundResponse response = service.refund(100L, new BigDecimal("50.00"), "Partial Refund Reason");

        assertThat(response).isNotNull();
        assertThat(response.amount()).isEqualByComparingTo("50.00");
        assertThat(response.remainingRefundableAmount()).isEqualByComparingTo("50.00"); // 100 - 50 = 50 remaining
    }

    @Test
    void refund_exceedsRemainingRefundable_throwsBadRequest() {
        Subscription sub = new Subscription();
        Payment original = new Payment();
        ReflectionTestUtils.setField(original, "id", 100L);
        original.setSubscription(sub);
        original.setType(PaymentType.CHARGE);
        original.setStatus(PaymentStatus.SUCCESS);
        original.setSucceededAt(Instant.now().minus(1, ChronoUnit.DAYS));
        original.setAmount(new BigDecimal("150.00"));

        Payment prevRefund = new Payment();
        prevRefund.setAmount(new BigDecimal("120.00"));
        prevRefund.setStatus(PaymentStatus.SUCCESS);

        when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(original));
        when(paymentRepository.findBySourcePaymentIdAndStatusIn(100L, List.of(PaymentStatus.PENDING, PaymentStatus.SUCCESS)))
                .thenReturn(List.of(prevRefund));

        // Remaining is 30.00, requesting 40.00 should fail
        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.refund(100L, new BigDecimal("40.00"), "Too high"));
        assertThat(ex.getErrorCode()).isEqualTo("EXCEEDS_REFUNDABLE_AMOUNT");
        assertThat(ex.getStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void refund_nonPositiveAmount_throwsBadRequest() {
        ApiException ex1 = catchThrowableOfType(ApiException.class, () -> service.refund(100L, BigDecimal.ZERO, "Zero"));
        assertThat(ex1.getErrorCode()).isEqualTo("INVALID_REFUND_AMOUNT");

        ApiException ex2 = catchThrowableOfType(ApiException.class, () -> service.refund(100L, new BigDecimal("-10.00"), "Negative"));
        assertThat(ex2.getErrorCode()).isEqualTo("INVALID_REFUND_AMOUNT");
    }

    @Test
    void refund_originalPaymentNotFound_throwsNotFound() {
        when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.empty());

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.refund(100L, new BigDecimal("10.00"), "Missing"));
        assertThat(ex.getErrorCode()).isEqualTo("PAYMENT_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    void refund_originalPaymentNotSuccessful_throwsBadRequest() {
        Payment original = new Payment();
        ReflectionTestUtils.setField(original, "id", 100L);
        original.setType(PaymentType.CHARGE);
        original.setStatus(PaymentStatus.FAILED); // not successful

        when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(original));

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.refund(100L, new BigDecimal("10.00"), "Failed payment"));
        assertThat(ex.getErrorCode()).isEqualTo("INVALID_PAYMENT_STATUS");
        assertThat(ex.getStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void refund_providerCallFails_returnsSafeGatewayError_andRecordsFailedRefundRow() {
        Subscription sub = new Subscription();
        Payment original = new Payment();
        ReflectionTestUtils.setField(original, "id", 100L);
        original.setSubscription(sub);
        original.setType(PaymentType.CHARGE);
        original.setStatus(PaymentStatus.SUCCESS);
        original.setSucceededAt(Instant.now().minus(1, ChronoUnit.DAYS));
        original.setAmount(new BigDecimal("150.00"));
        original.setCommissionRate(new BigDecimal("0.2000"));
        original.setProviderReference("prov-ref-123");

        when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(original));
        when(paymentRepository.findBySourcePaymentIdAndStatusIn(100L, List.of(PaymentStatus.PENDING, PaymentStatus.SUCCESS)))
                .thenReturn(List.of());
        when(iyzicoClient.refund(eq("prov-ref-123"), eq(new BigDecimal("50.00")), any()))
                .thenReturn(new RefundResult(false, null, "REFUND_ERROR", "Invalid transaction state"));

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.refund(100L, new BigDecimal("50.00"), "Reason"));
        assertThat(ex.getErrorCode()).isEqualTo("PROVIDER_REFUND_FAILED");
        assertThat(ex.getStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_GATEWAY);
        assertThat(ex.getMessage()).doesNotContain("Invalid transaction state");

        // The reservation (PENDING) is still recorded, then finalized to FAILED — an audit trail
        // of the attempt, and it frees the reserved amount back up for a future refund attempt.
        verify(paymentRepository, times(2)).saveAndFlush(argThat(p -> p.getType() == PaymentType.REFUND));
    }

    @Test
    void refund_afterInclusiveSevenDayWindow_isRejectedBeforeProviderCall() {
        Payment original = new Payment();
        ReflectionTestUtils.setField(original, "id", 100L);
        original.setSubscription(new Subscription());
        original.setType(PaymentType.CHARGE);
        original.setStatus(PaymentStatus.SUCCESS);
        original.setSucceededAt(Instant.now().minus(7, ChronoUnit.DAYS).minusSeconds(1));
        original.setAmount(new BigDecimal("150.00"));

        when(paymentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(original));
        when(paymentRepository.findBySourcePaymentIdAndStatusIn(100L,
                List.of(PaymentStatus.PENDING, PaymentStatus.SUCCESS))).thenReturn(List.of());

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.refund(100L, new BigDecimal("150.00"), "Expired"));

        assertThat(ex.getErrorCode()).isEqualTo("REFUND_WINDOW_EXPIRED");
        assertThat(ex.getMessage()).contains("7 günlük iade süresi doldu");
        verifyNoInteractions(iyzicoClient);
    }

    // --- admin subscription termination tests ---

    @Test
    void terminateSubscription_active_decrementsCapacity() {
        Subscription sub = new Subscription();
        ReflectionTestUtils.setField(sub, "id", 100L);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        CoachProfile coach = new CoachProfile();
        ReflectionTestUtils.setField(coach, "id", 50L);
        sub.setCoachProfile(coach);

        when(subscriptionRepository.findById(100L)).thenReturn(Optional.of(sub));

        AdminSubscriptionTerminateResponse response = service.terminateSubscription(100L, "Violation of terms");

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("TERMINATED");
        assertThat(response.subscriptionId()).isEqualTo(100L);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.TERMINATED);
        assertThat(sub.getTerminationReason()).isEqualTo("Violation of terms");

        verify(coachProfileRepository).decrementActiveStudentCount(50L);
        verify(subscriptionRepository).saveAndFlush(sub);
    }

    @Test
    void terminateSubscription_pendingPayment_doesNotDecrementCapacity() {
        Subscription sub = new Subscription();
        ReflectionTestUtils.setField(sub, "id", 100L);
        sub.setStatus(SubscriptionStatus.PENDING_PAYMENT);
        CoachProfile coach = new CoachProfile();
        ReflectionTestUtils.setField(coach, "id", 50L);
        sub.setCoachProfile(coach);

        when(subscriptionRepository.findById(100L)).thenReturn(Optional.of(sub));

        AdminSubscriptionTerminateResponse response = service.terminateSubscription(100L, "Cleanup");

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("TERMINATED");
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.TERMINATED);
        assertThat(sub.getTerminationReason()).isEqualTo("Cleanup");

        verify(coachProfileRepository, never()).decrementActiveStudentCount(any(Long.class));
        verify(subscriptionRepository).saveAndFlush(sub);
    }

    @Test
    void terminateSubscription_alreadyTerminated_idempotent() {
        Subscription sub = new Subscription();
        ReflectionTestUtils.setField(sub, "id", 100L);
        sub.setStatus(SubscriptionStatus.TERMINATED);
        Instant terminatedAt = Instant.now().minusSeconds(100);
        sub.setCancelledAt(terminatedAt);

        when(subscriptionRepository.findById(100L)).thenReturn(Optional.of(sub));

        AdminSubscriptionTerminateResponse response = service.terminateSubscription(100L, "Another reason");

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("TERMINATED");
        assertThat(response.terminatedAt()).isEqualTo(terminatedAt);
        assertThat(response.message()).contains("zaten sonlandırılmış");

        verify(coachProfileRepository, never()).decrementActiveStudentCount(any(Long.class));
        verify(subscriptionRepository, never()).saveAndFlush(any());
    }

    @Test
    void terminateSubscription_expiredOrCancelled_savesStatusButDoesNotDecrement() {
        Subscription sub = new Subscription();
        ReflectionTestUtils.setField(sub, "id", 100L);
        sub.setStatus(SubscriptionStatus.EXPIRED);
        CoachProfile coach = new CoachProfile();
        ReflectionTestUtils.setField(coach, "id", 50L);
        sub.setCoachProfile(coach);

        when(subscriptionRepository.findById(100L)).thenReturn(Optional.of(sub));

        AdminSubscriptionTerminateResponse response = service.terminateSubscription(100L, "Clean expired");

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("TERMINATED");
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.TERMINATED);

        verify(coachProfileRepository, never()).decrementActiveStudentCount(any(Long.class));
        verify(subscriptionRepository).saveAndFlush(sub);
    }

    @Test
    void listPayments_success() {
        Pageable pageable = PageRequest.of(0, 20);
        User student = new User();
        ReflectionTestUtils.setField(student, "email", "student@example.com");
        ReflectionTestUtils.setField(student, "fullName", "Student Name");

        User coachUser = new User();
        ReflectionTestUtils.setField(coachUser, "fullName", "Coach Name");
        CoachProfile coach = new CoachProfile();
        coach.setUser(coachUser);

        Package pkg = new Package();
        pkg.setName("Package Title");

        Subscription sub = new Subscription();
        ReflectionTestUtils.setField(sub, "id", 10L);
        sub.setStudent(student);
        sub.setCoachProfile(coach);
        sub.setPkg(pkg);

        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", 100L);
        payment.setSubscription(sub);
        payment.setType(PaymentType.CHARGE);
        payment.setAmount(new BigDecimal("150.00"));
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setSucceededAt(Instant.now().minus(1, ChronoUnit.DAYS));
        payment.setProviderReference("prov-ref");
        payment.setCommissionRate(new BigDecimal("0.2000"));
        payment.setCommissionAmount(new BigDecimal("30.00"));
        payment.setCoachPayoutAmount(new BigDecimal("120.00"));

        when(paymentRepository.findAllAdmin(pageable))
                .thenReturn(new PageImpl<>(List.of(payment)));
        when(paymentRepository.findBySourcePaymentIdInAndStatus(List.of(100L), PaymentStatus.SUCCESS)).thenReturn(List.of());

        var res = service.listPayments(pageable);
        assertThat(res).isNotNull();
        assertThat(res.content()).hasSize(1);
        assertThat(res.content().get(0).studentEmail()).isEqualTo("student@example.com");
        assertThat(res.content().get(0).remainingRefundableAmount()).isEqualTo(new BigDecimal("150.00"));
    }

    @Test
    void listSubscriptions_success() {
        Pageable pageable = PageRequest.of(0, 20);
        User student = new User();
        ReflectionTestUtils.setField(student, "email", "student@example.com");
        ReflectionTestUtils.setField(student, "fullName", "Student Name");

        User coachUser = new User();
        ReflectionTestUtils.setField(coachUser, "fullName", "Coach Name");
        CoachProfile coach = new CoachProfile();
        coach.setUser(coachUser);

        Package pkg = new Package();
        pkg.setName("Package Title");

        Subscription sub = new Subscription();
        ReflectionTestUtils.setField(sub, "id", 10L);
        sub.setStudent(student);
        sub.setCoachProfile(coach);
        sub.setPkg(pkg);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStartAt(Instant.now());
        sub.setEndAt(Instant.now());

        when(subscriptionRepository.findAllAdmin(pageable))
                .thenReturn(new PageImpl<>(List.of(sub)));

        var res = service.listSubscriptions(pageable);
        assertThat(res).isNotNull();
        assertThat(res.content()).hasSize(1);
        assertThat(res.content().get(0).studentEmail()).isEqualTo("student@example.com");
    }
}
