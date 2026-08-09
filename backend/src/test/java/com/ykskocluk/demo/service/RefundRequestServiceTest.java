package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.RefundRequestCreateRequest;
import com.ykskocluk.demo.entity.*;
import com.ykskocluk.demo.enums.*;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundRequestServiceTest {
    @Mock RefundRequestRepository requests; @Mock SubscriptionRepository subscriptions;
    @Mock PaymentRepository payments; @Mock SessionRepository sessions; @Mock UserRepository users;
    @Mock SubscriptionService subscriptionService; @Mock PlatformTransactionManager transactionManager;
    RefundRequestService service; User student; Subscription subscription; Payment payment;

    @BeforeEach void setup() {
        service = new RefundRequestService(requests, subscriptions, payments, sessions, users,
                subscriptionService, transactionManager);
        student = new User(); ReflectionTestUtils.setField(student, "id", 1L); student.setRole(Role.STUDENT); student.setFullName("Student");
        User coachUser = new User(); coachUser.setFullName("Coach");
        CoachProfile coach = new CoachProfile(); ReflectionTestUtils.setField(coach, "id", 2L); coach.setUser(coachUser);
        com.ykskocluk.demo.entity.Package pkg = new com.ykskocluk.demo.entity.Package(); ReflectionTestUtils.setField(pkg, "id", 3L); pkg.setName("Plan");
        subscription = new Subscription(); ReflectionTestUtils.setField(subscription, "id", 4L); subscription.setStudent(student); subscription.setCoachProfile(coach); subscription.setPkg(pkg);
        payment = new Payment(); ReflectionTestUtils.setField(payment, "id", 5L); payment.setSubscription(subscription); payment.setAmount(new BigDecimal("1500.00")); payment.setStatus(PaymentStatus.SUCCESS); payment.setType(PaymentType.CHARGE);
        lenient().when(users.findById(1L)).thenReturn(Optional.of(student)); lenient().when(subscriptions.findById(4L)).thenReturn(Optional.of(subscription));
        lenient().when(payments.findFirstBySubscriptionIdAndTypeAndStatusOrderBySucceededAtAsc(4L, PaymentType.CHARGE, PaymentStatus.SUCCESS)).thenReturn(Optional.of(payment));
        lenient().when(payments.findBySourcePaymentIdAndStatus(5L, PaymentStatus.SUCCESS)).thenReturn(List.of());
        lenient().when(requests.saveAndFlush(any())).thenAnswer(inv -> { RefundRequest r=inv.getArgument(0); ReflectionTestUtils.setField(r,"id",6L); return r; });
    }

    @Test void firstSevenDays_isUnconditional_evenWhenSessionStarted() {
        payment.setSucceededAt(Instant.now().minus(2, ChronoUnit.DAYS));
        Session completed = session(SessionStatus.COMPLETED, Instant.now().minus(1, ChronoUnit.DAYS));
        when(sessions.findBySubscriptionIdOrderByStartTimeAsc(4L)).thenReturn(List.of(completed));
        var response = service.create(1L, new RefundRequestCreateRequest(4L));
        assertThat(response.refundWindow()).isEqualTo(RefundWindow.UNCONDITIONAL);
        assertThat(response.serviceStarted()).isTrue();
    }

    @Test void daysEightToFourteen_withoutStartedService_isStandardEligible() {
        payment.setSucceededAt(Instant.now().minus(10, ChronoUnit.DAYS));
        when(sessions.findBySubscriptionIdOrderByStartTimeAsc(4L)).thenReturn(List.of(session(SessionStatus.PLANNED, Instant.now().plus(1, ChronoUnit.DAYS))));
        assertThat(service.create(1L, new RefundRequestCreateRequest(4L)).refundWindow()).isEqualTo(RefundWindow.STANDARD_ELIGIBLE);
    }

    @Test void daysEightToFourteen_withStartedService_isManualReview_notRejected() {
        payment.setSucceededAt(Instant.now().minus(10, ChronoUnit.DAYS));
        when(sessions.findBySubscriptionIdOrderByStartTimeAsc(4L)).thenReturn(List.of(session(SessionStatus.COMPLETED, Instant.now().minus(1, ChronoUnit.DAYS))));
        assertThat(service.create(1L, new RefundRequestCreateRequest(4L)).refundWindow()).isEqualTo(RefundWindow.MANUAL_REVIEW);
    }

    @Test void afterFourteenDays_isExpired() {
        payment.setSucceededAt(Instant.now().minus(15, ChronoUnit.DAYS));
        assertThatThrownBy(() -> service.create(1L, new RefundRequestCreateRequest(4L)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo("REFUND_WINDOW_EXPIRED"));
        verify(requests, never()).saveAndFlush(any());
    }

    @Test void anotherStudentsSubscription_isForbidden() {
        User owner = new User(); ReflectionTestUtils.setField(owner, "id", 99L); subscription.setStudent(owner);
        assertThatThrownBy(() -> service.create(1L, new RefundRequestCreateRequest(4L)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo("NOT_SUBSCRIPTION_OWNER"));
    }

    private Session session(SessionStatus status, Instant startsAt) { Session s=new Session(); s.setStatus(status); s.setStartTime(startsAt); s.setEndTime(startsAt.plusSeconds(3600)); return s; }
}
