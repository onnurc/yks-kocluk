package com.ykskocluk.demo.service;

import com.ykskocluk.demo.config.PaymentProperties;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import com.ykskocluk.demo.dto.SubscriptionCheckoutResponse;
import com.ykskocluk.demo.dto.SubscriptionCreateRequest;
import com.ykskocluk.demo.dto.SubscriptionResponse;
import com.ykskocluk.demo.dto.IyzicoWebhookRequest;
import com.ykskocluk.demo.dto.IyzicoWebhookResponse;
import com.ykskocluk.demo.dto.RefundResponse;
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
import com.ykskocluk.demo.mapper.SubscriptionMapper;
import com.ykskocluk.demo.integration.CheckoutResult;
import com.ykskocluk.demo.integration.IyzicoClient;
import com.ykskocluk.demo.integration.RefundResult;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.PackageRepository;
import com.ykskocluk.demo.repository.PaymentRepository;
import com.ykskocluk.demo.repository.SubscriptionRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PackageRepository packageRepository;
    private final CoachProfileRepository coachProfileRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentProperties paymentProperties;
    private final IyzicoClient iyzicoClient;
    private final SubscriptionMapper subscriptionMapper;
    private final EntityManager entityManager;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               PackageRepository packageRepository,
                               CoachProfileRepository coachProfileRepository,
                               UserRepository userRepository,
                               PaymentRepository paymentRepository,
                               PaymentProperties paymentProperties,
                               IyzicoClient iyzicoClient,
                               SubscriptionMapper subscriptionMapper,
                               EntityManager entityManager) {
        this.subscriptionRepository = subscriptionRepository;
        this.packageRepository = packageRepository;
        this.coachProfileRepository = coachProfileRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.paymentProperties = paymentProperties;
        this.iyzicoClient = iyzicoClient;
        this.subscriptionMapper = subscriptionMapper;
        this.entityManager = entityManager;
    }

    @Transactional
    public SubscriptionResponse subscribe(Long studentUserId, SubscriptionCreateRequest request) {
        Subscription subscription = createPendingSubscription(studentUserId, request);
        return subscriptionMapper.toResponse(subscription);
        }

        @Transactional
        public SubscriptionCheckoutResponse checkout(Long studentUserId, SubscriptionCreateRequest request) {
        Subscription subscription = createPendingSubscription(studentUserId, request);
        Payment payment = createPendingPayment(subscription);
        CheckoutResult checkoutResult = iyzicoClient.initializeCheckout(
            subscription.getId(), payment.getId(), payment.getAmount(), payment.getIdempotencyKey());

        return new SubscriptionCheckoutResponse(
            subscription.getId(),
            payment.getId(),
            subscription.getStatus(),
            payment.getStatus(),
            payment.getAmount(),
            checkoutResult.checkoutToken(),
            checkoutResult.checkoutUrl());
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> mySubscriptions(Long studentUserId) {
        return subscriptionRepository.findByStudentIdOrderByCreatedAtDesc(studentUserId).stream()
                .map(subscriptionMapper::toResponse)
                .toList();
    }

    private Subscription createPendingSubscription(Long studentUserId, SubscriptionCreateRequest request) {
        Package pkg = packageRepository.findById(request.packageId())
            .filter(pkgCandidate -> pkgCandidate.isActive())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PACKAGE_NOT_FOUND", "Paket bulunamadı"));

        // APPROVED-only (non-approved coaches are invisible/unbookable).
        CoachProfile coach = coachProfileRepository.findByIdAndStatus(request.coachId(), CoachProfileStatus.APPROVED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COACH_NOT_FOUND", "Koç bulunamadı"));

        if (subscriptionRepository.findLiveSubscription(studentUserId, coach.getId()).isPresent()
                || subscriptionRepository.existsByStudentIdAndCoachProfileIdAndStatus(
                studentUserId, coach.getId(), SubscriptionStatus.PENDING_PAYMENT)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_SUBSCRIBED",
                    "Bu koç ile zaten aktif aboneliğiniz var");
        }

        User student = userRepository.findById(studentUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Kullanıcı bulunamadı"));

        Instant now = Instant.now();
        Subscription subscription = new Subscription();
        subscription.setStudent(student);
        subscription.setCoachProfile(coach);
        subscription.setPkg(pkg);
        subscription.setStatus(SubscriptionStatus.PENDING_PAYMENT);
        subscription.setStartAt(now);
        subscription.setEndAt(now.plus(pkg.getDurationDays(), ChronoUnit.DAYS));
        subscription.setAutoRenew(true);
        subscription.setSavedCardToken("stub-card-token-" + UUID.randomUUID());

        try {
            subscriptionRepository.saveAndFlush(subscription);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_SUBSCRIBED",
                    "Bu koç ile zaten aktif aboneliğiniz var");
        }
        return subscription;
    }

    private Payment createPendingPayment(Subscription subscription) {
        BigDecimal amount = subscription.getPkg().getPrice();
        BigDecimal rate = paymentProperties.commissionRate();
        BigDecimal commission = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);

        Payment payment = new Payment();
        payment.setSubscription(subscription);
        payment.setType(PaymentType.CHARGE);
        payment.setAmount(amount);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setIdempotencyKey("checkout:" + subscription.getId());
        payment.setCommissionRate(rate);
        payment.setCommissionAmount(commission);
        payment.setCoachPayoutAmount(amount.subtract(commission));

        return paymentRepository.saveAndFlush(payment);
    }

    @Transactional
    public SubscriptionResponse succeedPayment(Long paymentId, Long studentUserId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Ödeme bulunamadı"));

        if (!payment.getSubscription().getStudent().getId().equals(studentUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_PAYMENT_OWNER", "Bu ödeme size ait değil");
        }

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return subscriptionMapper.toResponse(payment.getSubscription());
        }

        if (payment.getStatus() == PaymentStatus.FAILED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_STATUS", "Başarısız ödemeler onaylanamaz");
        }

        Subscription subscription = payment.getSubscription();
        if (subscription.getStatus() != SubscriptionStatus.PENDING_PAYMENT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SUBSCRIPTION_STATUS", "Abonelik ödeme bekler durumda değil");
        }

        completePaymentSuccess(payment, subscription, "stub-provider-ref-" + UUID.randomUUID());

        return subscriptionMapper.toResponse(subscription);
    }

    private void completePaymentSuccess(Payment payment, Subscription subscription, String providerReference) {
        int updated = coachProfileRepository.incrementActiveStudentCountIfRoom(subscription.getCoachProfile().getId());
        if (updated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "COACH_FULL", "Koçun kontenjanı dolu");
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setProviderReference(providerReference);
        paymentRepository.saveAndFlush(payment);

        Instant now = Instant.now();
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartAt(now);
        subscription.setEndAt(now.plus(subscription.getPkg().getDurationDays(), ChronoUnit.DAYS));
        subscriptionRepository.saveAndFlush(subscription);
    }

    @Transactional
    public IyzicoWebhookResponse processWebhook(IyzicoWebhookRequest request) {
        // TODO: Real signature verification belongs to later real iyzico integration

        Payment payment = paymentRepository.findById(request.paymentId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Ödeme bulunamadı"));

        if (!"SUCCESS".equalsIgnoreCase(request.status()) && !"FAILURE".equalsIgnoreCase(request.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_WEBHOOK_STATUS", "Geçersiz webhook durumu: " + request.status());
        }

        if ("SUCCESS".equalsIgnoreCase(request.status())) {
            if (payment.getStatus() == PaymentStatus.SUCCESS) {
                return new IyzicoWebhookResponse("IDEMPOTENT", "Abonelik ve ödeme zaten aktif edilmiş (idempotent)");
            }
            if (payment.getStatus() == PaymentStatus.FAILED) {
                return new IyzicoWebhookResponse("IDEMPOTENT", "Ödeme zaten başarısız olarak işaretlenmiş");
            }

            Subscription subscription = payment.getSubscription();
            if (subscription.getStatus() != SubscriptionStatus.PENDING_PAYMENT) {
                return new IyzicoWebhookResponse("IDEMPOTENT", "Abonelik zaten aktif edilmiş (idempotent)");
            }

            completePaymentSuccess(payment, subscription, request.providerReference() != null ? request.providerReference() : "webhook-provider-ref-" + UUID.randomUUID());
            return new IyzicoWebhookResponse("PROCESSED", "Ödeme başarıyla tamamlandı ve abonelik aktif edildi");
        } else {
            if (payment.getStatus() == PaymentStatus.FAILED) {
                return new IyzicoWebhookResponse("IDEMPOTENT", "Ödeme zaten başarısız olarak işaretlenmiş (idempotent)");
            }
            if (payment.getStatus() == PaymentStatus.SUCCESS) {
                return new IyzicoWebhookResponse("IDEMPOTENT", "Ödeme zaten başarıyla tamamlanmış");
            }

            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.saveAndFlush(payment);

            return new IyzicoWebhookResponse("PROCESSED", "Ödeme başarısız olarak işaretlendi");
        }
    }

    @Transactional
    public RefundResponse refund(Long paymentId, BigDecimal refundAmount, String reason) {
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REFUND_AMOUNT", "İade tutarı sıfırdan büyük olmalıdır");
        }

        Payment originalPayment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Ödeme bulunamadı"));

        if (originalPayment.getType() != PaymentType.CHARGE || originalPayment.getStatus() != PaymentStatus.SUCCESS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_STATUS", "Sadece başarılı ödemeler iade edilebilir");
        }

        // Calculate already refunded amount
        List<Payment> existingRefunds = paymentRepository.findBySourcePaymentIdAndStatus(paymentId, PaymentStatus.SUCCESS);
        BigDecimal totalRefunded = existingRefunds.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remainingRefundable = originalPayment.getAmount().subtract(totalRefunded);

        if (refundAmount.compareTo(remainingRefundable) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EXCEEDS_REFUNDABLE_AMOUNT",
                    String.format("İade tutarı kalan iade edilebilir tutarı (%s TRY) aşamaz", remainingRefundable));
        }

        // Deterministic idempotency key to prevent duplicate refunds
        int refundIndex = existingRefunds.size() + 1;
        String refundKey = "refund:" + paymentId + ":" + refundIndex;

        // Call Iyzico refund
        RefundResult refundResult = iyzicoClient.refund(originalPayment.getProviderReference(), refundAmount, refundKey);

        if (!refundResult.success()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROVIDER_REFUND_FAILED",
                    "Iyzico iade işlemi başarısız oldu: " + refundResult.errorMessage());
        }

        // Create REFUND payment row
        Payment refundPayment = new Payment();
        refundPayment.setSubscription(originalPayment.getSubscription());
        refundPayment.setType(PaymentType.REFUND);
        refundPayment.setAmount(refundAmount);
        refundPayment.setStatus(PaymentStatus.SUCCESS);
        refundPayment.setIdempotencyKey(refundKey);
        refundPayment.setProviderReference(refundResult.providerReference());

        // Snapshot commission details (pro-rata based on original rate)
        BigDecimal rate = originalPayment.getCommissionRate();
        BigDecimal refundCommission = refundAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        refundPayment.setCommissionRate(rate);
        refundPayment.setCommissionAmount(refundCommission);
        refundPayment.setCoachPayoutAmount(refundAmount.subtract(refundCommission));
        refundPayment.setSourcePayment(originalPayment);

        paymentRepository.saveAndFlush(refundPayment);

        // Force version increment on original payment to lock against concurrent modifications
        entityManager.lock(originalPayment, LockModeType.OPTIMISTIC_FORCE_INCREMENT);

        BigDecimal newRemaining = remainingRefundable.subtract(refundAmount);

        return new RefundResponse(
                paymentId,
                refundPayment.getId(),
                refundPayment.getStatus().name(),
                refundAmount,
                newRemaining,
                "İade işlemi başarıyla gerçekleştirildi"
        );
    }
}
