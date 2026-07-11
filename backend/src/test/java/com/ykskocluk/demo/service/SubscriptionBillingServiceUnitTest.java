package com.ykskocluk.demo.service;

import com.ykskocluk.demo.config.PaymentProperties;
import com.ykskocluk.demo.dto.SubscriptionResponse;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.SubscriptionStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.integration.IyzicoClient;
import com.ykskocluk.demo.mapper.SubscriptionMapper;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.PaymentRepository;
import com.ykskocluk.demo.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionBillingServiceUnitTest {

    @Mock SubscriptionRepository subscriptionRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock CoachProfileRepository coachProfileRepository;
    @Mock IyzicoClient iyzicoClient;
    @Mock PaymentProperties paymentProperties;
    @Mock SubscriptionMapper subscriptionMapper;
    @Mock PlatformTransactionManager transactionManager;
    @Mock TransactionStatus transactionStatus;

    SubscriptionBillingService service;

    private static final Long SUB_ID = 1L;
    private static final Long STUDENT_ID = 42L;
    private static final Long COACH_PROFILE_ID = 77L;

    private Subscription sub;
    private User student;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);

        service = new SubscriptionBillingService(
                subscriptionRepository,
                paymentRepository,
                coachProfileRepository,
                iyzicoClient,
                paymentProperties,
                subscriptionMapper,
                transactionManager
        );

        student = new User();
        ReflectionTestUtils.setField(student, "id", STUDENT_ID);

        CoachProfile coach = new CoachProfile();
        ReflectionTestUtils.setField(coach, "id", COACH_PROFILE_ID);

        sub = new Subscription();
        ReflectionTestUtils.setField(sub, "id", SUB_ID);
        sub.setStudent(student);
        sub.setCoachProfile(coach);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setAutoRenew(true);
        sub.setStartAt(Instant.parse("2026-07-01T09:00:00Z"));
        sub.setEndAt(Instant.parse("2026-07-31T09:00:00Z"));
    }

    @Test
    void cancel_activeSubscription_updatesAutoRenewAndCancelledAt() {
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.of(sub));
        when(subscriptionMapper.toResponse(sub)).thenReturn(new SubscriptionResponse(
                SUB_ID, COACH_PROFILE_ID, "Coach", 3L, "Pack", 1, SubscriptionStatus.ACTIVE, sub.getStartAt(), sub.getEndAt()
        ));

        SubscriptionResponse response = service.cancel(SUB_ID, STUDENT_ID);

        assertThat(response).isNotNull();
        assertThat(sub.isAutoRenew()).isFalse();
        assertThat(sub.getCancelledAt()).isNotNull();
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.getStartAt()).isEqualTo(Instant.parse("2026-07-01T09:00:00Z"));
        assertThat(sub.getEndAt()).isEqualTo(Instant.parse("2026-07-31T09:00:00Z"));

        verify(paymentRepository, never()).save(any());
        verify(coachProfileRepository, never()).decrementActiveStudentCount(any());
    }

    @Test
    void cancel_pastDueSubscription_updatesAutoRenewAndCancelledAt() {
        sub.setStatus(SubscriptionStatus.PAST_DUE);
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.of(sub));
        when(subscriptionMapper.toResponse(sub)).thenReturn(new SubscriptionResponse(
                SUB_ID, COACH_PROFILE_ID, "Coach", 3L, "Pack", 1, SubscriptionStatus.PAST_DUE, sub.getStartAt(), sub.getEndAt()
        ));

        SubscriptionResponse response = service.cancel(SUB_ID, STUDENT_ID);

        assertThat(response).isNotNull();
        assertThat(sub.isAutoRenew()).isFalse();
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
    }

    @Test
    void cancel_notOwner_throwsForbidden() {
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.of(sub));

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.cancel(SUB_ID, 999L));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getErrorCode()).isEqualTo("NOT_SUBSCRIPTION_OWNER");
        assertThat(sub.isAutoRenew()).isTrue();
    }

    @Test
    void cancel_pendingPayment_throwsConflict() {
        sub.setStatus(SubscriptionStatus.PENDING_PAYMENT);
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.of(sub));

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.cancel(SUB_ID, STUDENT_ID));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getErrorCode()).isEqualTo("SUBSCRIPTION_NOT_CANCELLABLE");
        assertThat(sub.isAutoRenew()).isTrue();
    }

    @Test
    void cancel_expired_throwsConflict() {
        sub.setStatus(SubscriptionStatus.EXPIRED);
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.of(sub));

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.cancel(SUB_ID, STUDENT_ID));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getErrorCode()).isEqualTo("SUBSCRIPTION_NOT_CANCELLABLE");
    }

    @Test
    void cancel_terminated_throwsConflict() {
        sub.setStatus(SubscriptionStatus.TERMINATED);
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.of(sub));

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.cancel(SUB_ID, STUDENT_ID));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getErrorCode()).isEqualTo("SUBSCRIPTION_NOT_CANCELLABLE");
    }

    @Test
    void cancel_subscriptionNotFound_throwsNotFound() {
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.empty());

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.cancel(SUB_ID, STUDENT_ID));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getErrorCode()).isEqualTo("SUBSCRIPTION_NOT_FOUND");
    }

    @Test
    void cancel_repeatedRequest_isIdempotent() {
        sub.setAutoRenew(false);
        sub.setCancelledAt(Instant.parse("2026-07-05T09:00:00Z"));
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.of(sub));
        when(subscriptionMapper.toResponse(sub)).thenReturn(new SubscriptionResponse(
                SUB_ID, COACH_PROFILE_ID, "Coach", 3L, "Pack", 1, SubscriptionStatus.ACTIVE, sub.getStartAt(), sub.getEndAt()
        ));

        SubscriptionResponse response = service.cancel(SUB_ID, STUDENT_ID);

        assertThat(response).isNotNull();
        assertThat(sub.isAutoRenew()).isFalse();
        assertThat(sub.getCancelledAt()).isNotEqualTo(Instant.parse("2026-07-05T09:00:00Z")); // Overwritten to now
    }
}
