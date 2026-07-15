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
import com.ykskocluk.demo.dto.AdminSubscriptionTerminateResponse;
import com.ykskocluk.demo.dto.AdminPaymentResponse;
import com.ykskocluk.demo.dto.AdminSubscriptionResponse;
import com.ykskocluk.demo.dto.PageResponse;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.Package;
import com.ykskocluk.demo.entity.Payment;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final com.ykskocluk.demo.config.IyzicoProperties iyzicoProperties;
    private final ConsentService consentService;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               PackageRepository packageRepository,
                               CoachProfileRepository coachProfileRepository,
                               UserRepository userRepository,
                               PaymentRepository paymentRepository,
                               PaymentProperties paymentProperties,
                               IyzicoClient iyzicoClient,
                               SubscriptionMapper subscriptionMapper,
                               EntityManager entityManager,
                               com.ykskocluk.demo.config.IyzicoProperties iyzicoProperties,
                               ConsentService consentService) {
        this.subscriptionRepository = subscriptionRepository;
        this.packageRepository = packageRepository;
        this.coachProfileRepository = coachProfileRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.paymentProperties = paymentProperties;
        this.iyzicoClient = iyzicoClient;
        this.subscriptionMapper = subscriptionMapper;
        this.entityManager = entityManager;
        this.iyzicoProperties = iyzicoProperties;
        this.consentService = consentService;
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

        consentService.checkConsentRequiredForAction(student);

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
        return processWebhook(request, null);
    }

    @Transactional
    public IyzicoWebhookResponse processWebhook(IyzicoWebhookRequest request, String signatureV3) {
        if (iyzicoProperties != null && iyzicoProperties.enabled()) {
            String secretKey = iyzicoProperties.secretKey();
            if (secretKey == null || secretKey.isBlank()) {
                secretKey = "";
            }

            if (signatureV3 == null || signatureV3.isBlank()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_SIGNATURE", "İmza başlığı eksik");
            }

            String iyziEventType = request.iyziEventType() != null ? request.iyziEventType() : "";
            String paymentIdStr = request.paymentId() != null ? request.paymentId().toString() : "";
            String paymentConversationId = request.paymentConversationId() != null ? request.paymentConversationId() : "";
            String statusStr = request.status() != null ? request.status() : "";

            String data = secretKey + iyziEventType + paymentIdStr + paymentConversationId + statusStr;
            String computedSignature = calculateHmacSha256(data, secretKey);

            if (!computedSignature.equalsIgnoreCase(signatureV3)) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_SIGNATURE", "İmza doğrulanamadı");
            }
        }

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

        Payment originalPayment = paymentRepository.findByIdForUpdate(paymentId)
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

    @Transactional
    public AdminSubscriptionTerminateResponse terminateSubscription(Long id, String reason) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SUBSCRIPTION_NOT_FOUND",
                        "Abonelik bulunamadı"));

        if (subscription.getStatus() == SubscriptionStatus.TERMINATED) {
            return new AdminSubscriptionTerminateResponse(
                    id,
                    SubscriptionStatus.TERMINATED.name(),
                    subscription.getCancelledAt(),
                    "Abonelik zaten sonlandırılmış durumda"
            );
        }

        if (subscription.getStatus() == SubscriptionStatus.EXPIRED || subscription.getStatus() == SubscriptionStatus.CANCELLED) {
            subscription.setStatus(SubscriptionStatus.TERMINATED);
            subscription.setCancelledAt(Instant.now());
            subscription.setTerminationReason(reason);
            subscriptionRepository.saveAndFlush(subscription);
            return new AdminSubscriptionTerminateResponse(
                    id,
                    SubscriptionStatus.TERMINATED.name(),
                    subscription.getCancelledAt(),
                    "Abonelik sonlandırıldı (zaten aktif değildi)"
            );
        }

        boolean wasActive = (subscription.getStatus() == SubscriptionStatus.ACTIVE ||
                             subscription.getStatus() == SubscriptionStatus.PAST_DUE);

        subscription.setStatus(SubscriptionStatus.TERMINATED);
        subscription.setCancelledAt(Instant.now());
        subscription.setTerminationReason(reason);
        subscription.setAutoRenew(false);
        subscriptionRepository.saveAndFlush(subscription);

        if (wasActive) {
            coachProfileRepository.decrementActiveStudentCount(subscription.getCoachProfile().getId());
        }

        return new AdminSubscriptionTerminateResponse(
                id,
                SubscriptionStatus.TERMINATED.name(),
                subscription.getCancelledAt(),
                "Abonelik başarıyla sonlandırıldı"
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminPaymentResponse> listPayments(Pageable pageable) {
        Page<Payment> page = paymentRepository.findAll(pageable);
        Page<AdminPaymentResponse> mapped = page.map(payment -> {
            BigDecimal totalRefunded = BigDecimal.ZERO;
            if (payment.getType() == PaymentType.CHARGE) {
                List<Payment> existingRefunds = paymentRepository.findBySourcePaymentIdAndStatus(payment.getId(), PaymentStatus.SUCCESS);
                totalRefunded = existingRefunds.stream()
                        .map(Payment::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            BigDecimal remainingRefundable = payment.getAmount().subtract(totalRefunded);

            return new AdminPaymentResponse(
                    payment.getId(),
                    payment.getSubscription().getId(),
                    payment.getSubscription().getStudent().getEmail(),
                    payment.getSubscription().getStudent().getFullName(),
                    payment.getSubscription().getCoachProfile().getUser().getFullName(),
                    payment.getSubscription().getPkg().getName(),
                    payment.getType().name(),
                    payment.getAmount(),
                    payment.getStatus().name(),
                    payment.getProviderReference(),
                    payment.getCreatedAt(),
                    totalRefunded,
                    remainingRefundable
            );
        });
        return PageResponse.from(mapped);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminSubscriptionResponse> listSubscriptions(Pageable pageable) {
        Page<Subscription> page = subscriptionRepository.findAll(pageable);
        Page<AdminSubscriptionResponse> mapped = page.map(sub -> new AdminSubscriptionResponse(
                sub.getId(),
                sub.getStudent().getEmail(),
                sub.getStudent().getFullName(),
                sub.getCoachProfile().getUser().getFullName(),
                sub.getPkg().getName(),
                sub.getStatus().name(),
                sub.getStartAt(),
                sub.getEndAt(),
                sub.isAutoRenew(),
                sub.getCancelledAt(),
                sub.getTerminationReason(),
                sub.getCreatedAt()
        ));
        return PageResponse.from(mapped);
    }

    private String calculateHmacSha256(String data, String key) {
        try {
            javax.crypto.Mac sha256HMAC = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKeySpec = new javax.crypto.spec.SecretKeySpec(
                    key.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
            sha256HMAC.init(secretKeySpec);
            byte[] hashBytes = sha256HMAC.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate HMAC-SHA256", e);
        }
    }
}
