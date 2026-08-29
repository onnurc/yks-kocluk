package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.*;
import com.ykskocluk.demo.entity.*;
import com.ykskocluk.demo.entity.RefundRequest;
import com.ykskocluk.demo.enums.*;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.util.List;
import java.util.Set;

@Service
public class RefundRequestService {
    private static final Set<String> SORT_FIELDS = Set.of("requestedAt", "purchaseAt", "id");
    private static final List<RefundRequestStatus> ACTIVE_STATUSES =
            List.of(RefundRequestStatus.PENDING, RefundRequestStatus.APPROVED);

    private final RefundRequestRepository requests;
    private final SubscriptionRepository subscriptions;
    private final PaymentRepository payments;
    private final SessionRepository sessions;
    private final UserRepository users;
    private final SubscriptionService subscriptionService;
    private final TransactionTemplate tx;
    private final Clock clock;

    public RefundRequestService(RefundRequestRepository requests, SubscriptionRepository subscriptions,
                                PaymentRepository payments, SessionRepository sessions, UserRepository users,
                                SubscriptionService subscriptionService, PlatformTransactionManager transactionManager,
                                Clock clock) {
        this.requests = requests;
        this.subscriptions = subscriptions;
        this.payments = payments;
        this.sessions = sessions;
        this.users = users;
        this.subscriptionService = subscriptionService;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Transactional
    public RefundRequestResponse create(Long studentId, RefundRequestCreateRequest input) {
        User student = requireRole(studentId, Role.STUDENT);
        Subscription subscription = subscriptions.findById(input.subscriptionId())
                .orElseThrow(() -> notFound("SUBSCRIPTION_NOT_FOUND", "Abonelik bulunamadı"));
        if (!subscription.getStudent().getId().equals(studentId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_SUBSCRIPTION_OWNER", "Bu abonelik size ait değil");
        }
        Payment original = payments.findFirstBySubscriptionIdAndTypeAndStatusOrderBySucceededAtAsc(
                        subscription.getId(), PaymentType.CHARGE, PaymentStatus.SUCCESS)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "NO_SUCCESSFUL_PURCHASE",
                        "Başarılı bir plan ödemesi bulunamadı"));
        Instant purchaseAt = original.getSucceededAt();
        if (purchaseAt == null) {
            throw new ApiException(HttpStatus.CONFLICT, "PURCHASE_TIMESTAMP_UNAVAILABLE",
                    "Satın alma zamanı doğrulanamadı");
        }
        Instant now = clock.instant();
        BigDecimal refunded = refundedAmount(original.getId());
        BigDecimal remaining = original.getAmount().subtract(refunded);
        if (remaining.signum() <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_ALREADY_REFUNDED", "Ödeme zaten tamamen iade edildi");
        }
        RefundPolicy.Decision policy = RefundPolicy.evaluate(purchaseAt, now, remaining);
        if (!policy.eligible()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REFUND_WINDOW_EXPIRED",
                    policy.ineligibleReason());
        }
        if (requests.existsByOriginalPaymentIdAndStatusIn(original.getId(), ACTIVE_STATUSES)) {
            throw new ApiException(HttpStatus.CONFLICT, "ACTIVE_REFUND_REQUEST_EXISTS",
                    "Bu satın alma için aktif bir iade talebi zaten var");
        }
        // Session evidence is operational audit data only. It never changes the seven-day decision above.
        Evidence evidence = evidence(subscription.getId(), now);
        RefundWindow window = RefundWindow.UNCONDITIONAL;

        RefundRequest request = new RefundRequest();
        request.setStudent(student);
        request.setSubscription(subscription);
        request.setOriginalPayment(original);
        request.setRequestedAt(now);
        request.setPurchaseAt(purchaseAt);
        request.setRefundWindow(window);
        request.setStatus(RefundRequestStatus.PENDING);
        request.setServiceStarted(evidence.serviceStarted);
        request.setPaidSessionsCount(evidence.paidCount);
        request.setCompletedSessionsCount(evidence.completedCount);
        request.setEarliestPaidSessionAt(evidence.earliest);
        request.setLatestRelevantSessionStatus(evidence.latestStatus);
        try {
            return toResponse(requests.saveAndFlush(request));
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "ACTIVE_REFUND_REQUEST_EXISTS",
                    "Bu satın alma için aktif bir iade talebi zaten var");
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<RefundRequestResponse> mine(Long studentId, Pageable pageable) {
        requireRole(studentId, Role.STUDENT);
        validateSort(pageable);
        return PageResponse.from(requests.findByStudentIdOrderByRequestedAtDesc(studentId, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public RefundEligibilityResponse eligibility(Long studentId, Long subscriptionId) {
        requireRole(studentId, Role.STUDENT);
        Subscription subscription = subscriptions.findById(subscriptionId)
                .orElseThrow(() -> notFound("SUBSCRIPTION_NOT_FOUND", "Abonelik bulunamadı"));
        if (!subscription.getStudent().getId().equals(studentId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_SUBSCRIPTION_OWNER", "Bu abonelik size ait değil");
        }
        Payment original = payments.findFirstBySubscriptionIdAndTypeAndStatusOrderBySucceededAtAsc(
                        subscriptionId, PaymentType.CHARGE, PaymentStatus.SUCCESS)
                .orElse(null);
        if (original == null || original.getSucceededAt() == null) {
            return new RefundEligibilityResponse(subscriptionId, false, RefundEligibilityStatus.PAYMENT_NOT_ELIGIBLE,
                    BigDecimal.ZERO, "TRY", null,
                    "İade edilebilir başarılı bir ödeme bulunamadı.", null);
        }
        BigDecimal remaining = original.getAmount().subtract(refundedAmount(original.getId()));
        RefundPolicy.Decision policy = RefundPolicy.evaluate(original.getSucceededAt(), clock.instant(), remaining);
        RefundRequestStatus activeStatus = requests
                .findFirstByOriginalPaymentIdAndStatusIn(original.getId(), ACTIVE_STATUSES)
                .map(RefundRequest::getStatus).orElse(null);
        RefundEligibilityStatus status;
        String explanation;
        if (remaining.signum() <= 0) {
            status = RefundEligibilityStatus.NO_REFUNDABLE_BALANCE;
            explanation = "Bu ödeme için iade edilebilir bakiye kalmadı.";
        } else if (activeStatus != null) {
            status = RefundEligibilityStatus.ACTIVE_REQUEST_EXISTS;
            explanation = "Bu ödeme için işleme alınmış bir iade talebiniz var.";
        } else if (!policy.eligible()) {
            status = RefundEligibilityStatus.WINDOW_EXPIRED;
            explanation = policy.ineligibleReason();
        } else {
            status = RefundEligibilityStatus.ELIGIBLE;
            explanation = "Ödeme tarihinden itibaren ilk 7 gün içinde koşulsuz iade talebi oluşturabilirsiniz.";
        }
        return new RefundEligibilityResponse(subscriptionId, status == RefundEligibilityStatus.ELIGIBLE, status,
                remaining.max(BigDecimal.ZERO), "TRY", policy.deadline(), explanation, activeStatus);
    }

    @Transactional(readOnly = true)
    public PageResponse<RefundRequestResponse> adminList(RefundRequestStatus status, RefundWindow window,
            Long studentId, Long coachId, Instant from, Instant to, Pageable pageable) {
        validateRange(from, to); validateSort(pageable);
        return PageResponse.from(requests.searchAdmin(status, window, studentId, coachId, from, to, pageable)
                .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public RefundRequestResponse adminDetail(Long id) {
        return toResponse(requests.findDetailedById(id)
                .orElseThrow(() -> notFound("REFUND_REQUEST_NOT_FOUND", "İade talebi bulunamadı")));
    }

    public RefundRequestResponse approve(Long adminId, Long id) {
        Approval approval = tx.execute(ignored -> reserveApproval(adminId, id));
        if (approval == null) return adminDetail(id);
        try {
            var result = subscriptionService.refund(approval.paymentId, approval.amount, "Refund request #" + id);
            tx.executeWithoutResult(ignored -> completeApproval(id, adminId, result.refundPaymentId()));
            return adminDetail(id);
        } catch (RuntimeException ex) {
            tx.executeWithoutResult(ignored -> releaseFailedApproval(id));
            throw ex;
        }
    }

    @Transactional
    public RefundRequestResponse reject(Long adminId, Long id, RefundRequestRejectRequest input) {
        User admin = requireRole(adminId, Role.ADMIN);
        RefundRequest request = lock(id);
        if (request.getStatus() == RefundRequestStatus.REJECTED) return toResponse(request);
        if (request.getStatus() != RefundRequestStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "REFUND_REQUEST_NOT_PENDING", "İade talebi beklemede değil");
        }
        request.setStatus(RefundRequestStatus.REJECTED);
        request.setAdminDecisionBy(admin);
        request.setAdminDecisionAt(clock.instant());
        request.setRejectionReason(input.reason().trim());
        return toResponse(requests.saveAndFlush(request));
    }

    private Approval reserveApproval(Long adminId, Long id) {
        User admin = requireRole(adminId, Role.ADMIN);
        RefundRequest request = lock(id);
        if (request.getStatus() == RefundRequestStatus.REFUNDED) {
            return null;
        }
        if (request.getStatus() == RefundRequestStatus.APPROVED) {
            Payment completedRefund = payments.findBySourcePaymentIdAndStatus(
                            request.getOriginalPayment().getId(), PaymentStatus.SUCCESS).stream()
                    .filter(p -> request.getAdminDecisionAt() == null || p.getCreatedAt() == null
                            || !p.getCreatedAt().isBefore(request.getAdminDecisionAt()))
                    .findFirst().orElse(null);
            if (completedRefund != null) {
                request.setRefundPayment(completedRefund);
                request.setStatus(RefundRequestStatus.REFUNDED);
                requests.saveAndFlush(request);
                return null;
            }
            throw new ApiException(HttpStatus.CONFLICT, "REFUND_APPROVAL_IN_PROGRESS",
                    "İade onayı halen işleniyor");
        }
        if (request.getStatus() != RefundRequestStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "REFUND_REQUEST_NOT_PENDING", "İade talebi beklemede değil");
        }
        BigDecimal remaining = request.getOriginalPayment().getAmount()
                .subtract(refundedAmount(request.getOriginalPayment().getId()));
        if (remaining.signum() <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_ALREADY_REFUNDED", "Ödeme zaten tamamen iade edildi");
        }
        request.setStatus(RefundRequestStatus.APPROVED);
        request.setAdminDecisionBy(admin);
        request.setAdminDecisionAt(clock.instant());
        requests.saveAndFlush(request);
        return new Approval(request.getOriginalPayment().getId(), remaining);
    }

    private void completeApproval(Long id, Long adminId, Long refundPaymentId) {
        RefundRequest request = lock(id);
        request.setRefundPayment(payments.findById(refundPaymentId).orElseThrow());
        request.setStatus(RefundRequestStatus.REFUNDED);
        request.setAdminDecisionBy(requireRole(adminId, Role.ADMIN));
        request.setAdminDecisionAt(clock.instant());
        requests.saveAndFlush(request);
    }

    private void releaseFailedApproval(Long id) {
        RefundRequest request = lock(id);
        if (request.getStatus() == RefundRequestStatus.APPROVED && request.getRefundPayment() == null) {
            request.setStatus(RefundRequestStatus.PENDING);
            request.setAdminDecisionAt(null);
            request.setAdminDecisionBy(null);
            requests.saveAndFlush(request);
        }
    }

    private Evidence evidence(Long subscriptionId, Instant now) {
        List<Session> all = sessions.findBySubscriptionIdOrderByStartTimeAsc(subscriptionId);
        int completed = (int) all.stream().filter(s -> s.getStatus() == SessionStatus.COMPLETED).count();
        boolean started = all.stream().anyMatch(s -> s.getStatus() == SessionStatus.COMPLETED
                || (s.getStartTime().compareTo(now) <= 0 && s.getStatus() != SessionStatus.CANCELLED));
        Session latestRelevant = all.stream().filter(s -> s.getStartTime().compareTo(now) <= 0)
                .reduce((a, b) -> b).orElse(null);
        return new Evidence(started, all.size(), completed,
                all.isEmpty() ? null : all.getFirst().getStartTime(),
                latestRelevant == null ? null : latestRelevant.getStatus().name());
    }

    private BigDecimal refundedAmount(Long paymentId) {
        return payments.findBySourcePaymentIdAndStatus(paymentId, PaymentStatus.SUCCESS).stream()
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private RefundRequestResponse toResponse(RefundRequest r) {
        BigDecimal refunded = refundedAmount(r.getOriginalPayment().getId());
        return new RefundRequestResponse(r.getId(), r.getStatus(), r.getRefundWindow(), r.getRequestedAt(),
                r.getPurchaseAt(), Math.max(0, Duration.between(r.getPurchaseAt(), clock.instant()).toSeconds()),
                r.getStudent().getId(), r.getStudent().getFullName(), r.getSubscription().getCoachProfile().getId(),
                r.getSubscription().getCoachProfile().getUser().getFullName(), r.getSubscription().getPkg().getId(),
                r.getSubscription().getPkg().getName(), r.getSubscription().getId(), r.getOriginalPayment().getId(),
                r.getOriginalPayment().getAmount(), refunded, "TRY", r.isServiceStarted(), r.getPaidSessionsCount(),
                r.getCompletedSessionsCount(), r.getEarliestPaidSessionAt(), r.getLatestRelevantSessionStatus(),
                r.getAdminDecisionAt(), r.getRejectionReason(),
                r.getRefundPayment() == null ? null : r.getRefundPayment().getId());
    }

    private User requireRole(Long id, Role role) {
        User user = users.findById(id).orElseThrow(() -> notFound("USER_NOT_FOUND", "Kullanıcı bulunamadı"));
        if (user.getRole() != role) throw new ApiException(HttpStatus.FORBIDDEN, "ROLE_NOT_ALLOWED", "Bu işlem için yetkiniz yok");
        return user;
    }
    private RefundRequest lock(Long id) { return requests.findByIdForUpdate(id).orElseThrow(() -> notFound("REFUND_REQUEST_NOT_FOUND", "İade talebi bulunamadı")); }
    private void validateRange(Instant from, Instant to) { if (from != null && to != null && !to.isAfter(from)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "Bitiş başlangıçtan sonra olmalı"); }
    private void validateSort(Pageable p) { for (Sort.Order o : p.getSort()) if (!SORT_FIELDS.contains(o.getProperty())) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SORT_FIELD", "Bu alana göre sıralama yapılamaz: " + o.getProperty()); }
    private ApiException notFound(String code, String detail) { return new ApiException(HttpStatus.NOT_FOUND, code, detail); }
    private record Evidence(boolean serviceStarted, int paidCount, int completedCount, Instant earliest, String latestStatus) {}
    private record Approval(Long paymentId, BigDecimal amount) {}
}
