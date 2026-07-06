package com.ykskocluk.demo.service;

import com.ykskocluk.demo.config.PaymentProperties;
import com.ykskocluk.demo.dto.SubscriptionCheckoutResponse;
import com.ykskocluk.demo.dto.SubscriptionCreateRequest;
import com.ykskocluk.demo.dto.SubscriptionResponse;
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
}
