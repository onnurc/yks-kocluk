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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundRequestServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-29T10:00:00Z");

    @Mock RefundRequestRepository requests; @Mock SubscriptionRepository subscriptions;
    @Mock PaymentRepository payments; @Mock SessionRepository sessions; @Mock UserRepository users;
    @Mock SubscriptionService subscriptionService; @Mock PlatformTransactionManager transactionManager;
    RefundRequestService service; User student; Subscription subscription; Payment payment;
    AtomicReference<RefundRequest> savedRequest;

    @BeforeEach void setup() {
        service = new RefundRequestService(requests, subscriptions, payments, sessions, users,
                subscriptionService, transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));
        student = new User(); ReflectionTestUtils.setField(student, "id", 1L); student.setRole(Role.STUDENT); student.setFullName("Student");
        User coachUser = new User(); coachUser.setFullName("Coach");
        CoachProfile coach = new CoachProfile(); ReflectionTestUtils.setField(coach, "id", 2L); coach.setUser(coachUser);
        com.ykskocluk.demo.entity.Package pkg = new com.ykskocluk.demo.entity.Package(); ReflectionTestUtils.setField(pkg, "id", 3L); pkg.setName("Plan");
        subscription = new Subscription(); ReflectionTestUtils.setField(subscription, "id", 4L); subscription.setStudent(student); subscription.setCoachProfile(coach); subscription.setPkg(pkg);
        payment = new Payment(); ReflectionTestUtils.setField(payment, "id", 5L); payment.setSubscription(subscription); payment.setAmount(new BigDecimal("1500.00")); payment.setStatus(PaymentStatus.SUCCESS); payment.setType(PaymentType.CHARGE); payment.setSucceededAt(NOW.minusSeconds(2 * 86400));
        lenient().when(users.findById(1L)).thenReturn(Optional.of(student)); lenient().when(subscriptions.findById(4L)).thenReturn(Optional.of(subscription));
        lenient().when(payments.findFirstBySubscriptionIdAndTypeAndStatusOrderBySucceededAtAsc(4L, PaymentType.CHARGE, PaymentStatus.SUCCESS)).thenReturn(Optional.of(payment));
        lenient().when(payments.findBySourcePaymentIdAndStatus(5L, PaymentStatus.SUCCESS)).thenReturn(List.of());
        savedRequest = new AtomicReference<>();
        lenient().when(requests.saveAndFlush(any())).thenAnswer(inv -> { RefundRequest r=inv.getArgument(0); ReflectionTestUtils.setField(r,"id",6L); savedRequest.set(r); return r; });
        lenient().when(requests.findDetailedById(6L)).thenAnswer(inv -> Optional.ofNullable(savedRequest.get()));
        lenient().when(subscriptionService.refundEligibleStudent(5L, 6L))
                .thenReturn(new com.ykskocluk.demo.dto.RefundResponse(5L, 7L, "SUCCESS", new BigDecimal("1500.00"), BigDecimal.ZERO, "ok"));
    }

    @Test void successfulPaymentWellInsideWindow_isEligible() {
        var response = service.eligibility(1L, 4L);
        assertThat(response.eligible()).isTrue();
        assertThat(response.status()).isEqualTo(RefundEligibilityStatus.ELIGIBLE);
        assertThat(response.refundableAmount()).isEqualByComparingTo("1500.00");
    }

    @Test void sessionsAlreadyUsedInsideWindow_remainUnconditionallyEligible() {
        Session completed = session(SessionStatus.COMPLETED, NOW.minusSeconds(86400));
        when(sessions.findBySubscriptionIdOrderByStartTimeAsc(4L)).thenReturn(List.of(completed));
        var response = service.create(1L, new RefundRequestCreateRequest(4L));
        assertThat(response.refundWindow()).isEqualTo(RefundWindow.UNCONDITIONAL);
        assertThat(response.serviceStarted()).isTrue();
        assertThat(response.amount()).isEqualByComparingTo("1500.00");
    }

    @Test void oneNanosecondBeforeExpiry_isEligible() {
        payment.setSucceededAt(NOW.minus(RefundPolicy.WINDOW).plusNanos(1));
        assertThat(service.eligibility(1L, 4L).status()).isEqualTo(RefundEligibilityStatus.ELIGIBLE);
    }

    @Test void exactExpiryAndImmediatelyAfter_areIneligible() {
        payment.setSucceededAt(NOW.minus(RefundPolicy.WINDOW));
        assertThat(service.eligibility(1L, 4L).status()).isEqualTo(RefundEligibilityStatus.WINDOW_EXPIRED);
        payment.setSucceededAt(NOW.minus(RefundPolicy.WINDOW).minusNanos(1));
        assertThat(service.eligibility(1L, 4L).status()).isEqualTo(RefundEligibilityStatus.WINDOW_EXPIRED);
    }

    @Test void noSessionsUsedAfterExpiry_isStillRejectedWithoutSessionLookup() {
        payment.setSucceededAt(NOW.minusSeconds(10 * 86400));
        assertThatThrownBy(() -> service.create(1L, new RefundRequestCreateRequest(4L)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("REFUND_WINDOW_EXPIRED"));
        verifyNoInteractions(sessions);
    }

    @Test void noRemainingRefundableBalance_isIneligibleAndUsesSpecificCreateCode() {
        Payment refund = new Payment(); refund.setAmount(new BigDecimal("1500.00"));
        when(payments.findBySourcePaymentIdAndStatus(5L, PaymentStatus.SUCCESS)).thenReturn(List.of(refund));
        assertThat(service.eligibility(1L, 4L).status())
                .isEqualTo(RefundEligibilityStatus.NO_REFUNDABLE_BALANCE);
        assertThatThrownBy(() -> service.create(1L, new RefundRequestCreateRequest(4L)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("PAYMENT_ALREADY_REFUNDED"));
    }

    @Test void activeRefundRequest_isIneligibleAndDuplicateCreateResumesIdempotently() {
        RefundRequest active = savedAuditRequest(); active.setStatus(RefundRequestStatus.PENDING);
        when(requests.findFirstByOriginalPaymentIdAndStatusIn(eq(5L), any())).thenReturn(Optional.of(active));
        when(requests.findDetailedById(6L)).thenReturn(Optional.of(active));
        var eligibility = service.eligibility(1L, 4L);
        assertThat(eligibility.status()).isEqualTo(RefundEligibilityStatus.ACTIVE_REQUEST_EXISTS);
        assertThat(eligibility.activeRequestStatus()).isEqualTo(RefundRequestStatus.PENDING);
        service.create(1L, new RefundRequestCreateRequest(4L));
        verify(subscriptionService).refundEligibleStudent(5L, 6L);
    }

    @Test void reservedProviderOperationRemainsRetryableAfterWindowWithSameAuditRequest() {
        payment.setSucceededAt(NOW.minus(RefundPolicy.WINDOW).minusSeconds(1));
        RefundRequest active = savedAuditRequest(); active.setStatus(RefundRequestStatus.PENDING);
        Payment pendingRefund = new Payment(); pendingRefund.setStatus(PaymentStatus.PENDING);
        active.setRefundPayment(pendingRefund);
        when(requests.findFirstByOriginalPaymentIdAndStatusIn(eq(5L), any())).thenReturn(Optional.of(active));
        when(requests.findDetailedById(6L)).thenReturn(Optional.of(active));

        assertThat(service.eligibility(1L, 4L).status()).isEqualTo(RefundEligibilityStatus.ELIGIBLE);
        service.create(1L, new RefundRequestCreateRequest(4L));
        verify(subscriptionService).refundEligibleStudent(5L, 6L);
    }

    @Test void anotherStudentsSubscription_isForbidden() {
        User owner = new User(); ReflectionTestUtils.setField(owner, "id", 99L); subscription.setStudent(owner);
        assertThatThrownBy(() -> service.eligibility(1L, 4L))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("NOT_SUBSCRIPTION_OWNER"));
        assertThatThrownBy(() -> service.create(1L, new RefundRequestCreateRequest(4L)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("NOT_SUBSCRIPTION_OWNER"));
    }

    @Test void refundAmountIsDerivedFromTrustedPaymentRecord() {
        var response = service.create(1L, new RefundRequestCreateRequest(4L));
        assertThat(response.amount()).isEqualByComparingTo(payment.getAmount());
        verify(requests).saveAndFlush(argThat(request -> request.getOriginalPayment() == payment));
    }

    @Test void missingSuccessfulCharge_hasStableIneligibleStatus() {
        when(payments.findFirstBySubscriptionIdAndTypeAndStatusOrderBySucceededAtAsc(4L, PaymentType.CHARGE, PaymentStatus.SUCCESS))
                .thenReturn(Optional.empty());
        var response = service.eligibility(1L, 4L);
        assertThat(response.status()).isEqualTo(RefundEligibilityStatus.PAYMENT_NOT_ELIGIBLE);
        assertThat(response.eligible()).isFalse();
    }

    private Session session(SessionStatus status, Instant startsAt) {
        Session session = new Session(); session.setStatus(status); session.setStartTime(startsAt);
        session.setEndTime(startsAt.plusSeconds(3600)); return session;
    }

    @Test void unconditionalAutomaticRefundCannotBeRejectedByAdmin() {
        User admin = new User(); admin.setRole(Role.ADMIN); when(users.findById(8L)).thenReturn(Optional.of(admin));
        RefundRequest request = savedAuditRequest(); when(requests.findByIdForUpdate(6L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.reject(8L, 6L,
                new com.ykskocluk.demo.dto.RefundRequestRejectRequest("manual decision")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("AUTOMATIC_REFUND_NO_ADMIN_DECISION"));
        verify(requests, never()).saveAndFlush(request);
    }

    @Test void unfilteredAdminAuditUsesTypedFindAllQuery() {
        var pageable = PageRequest.of(0, 20, org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "requestedAt"));
        when(requests.findAllAdmin(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        var response = service.adminList(null, null, null, null, null, null, pageable);

        assertThat(response.content()).isEmpty();
        verify(requests).findAllAdmin(pageable);
        verify(requests, never()).searchAdmin(any(), any(), any(), any(), any(), any(), any());
    }

    @Test void filteredAdminAuditStillUsesSearchQuery() {
        var pageable = PageRequest.of(0, 20);
        when(requests.searchAdmin(eq(RefundRequestStatus.REFUNDED), isNull(), isNull(), isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.adminList(RefundRequestStatus.REFUNDED, null, null, null, null, null, pageable);

        verify(requests).searchAdmin(RefundRequestStatus.REFUNDED, null, null, null, null, null, pageable);
    }

    private RefundRequest savedAuditRequest() {
        RefundRequest request = new RefundRequest(); ReflectionTestUtils.setField(request, "id", 6L);
        request.setStudent(student); request.setSubscription(subscription); request.setOriginalPayment(payment);
        request.setRequestedAt(NOW); request.setPurchaseAt(payment.getSucceededAt());
        request.setRefundWindow(RefundWindow.UNCONDITIONAL); request.setStatus(RefundRequestStatus.PENDING);
        return request;
    }
}
