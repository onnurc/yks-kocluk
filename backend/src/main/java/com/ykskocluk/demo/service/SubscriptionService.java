package com.ykskocluk.demo.service;

import com.ykskocluk.demo.config.PaymentProperties;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import com.ykskocluk.demo.dto.SubscriptionCheckoutResponse;
import com.ykskocluk.demo.dto.SubscriptionCheckoutRequest;
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
import com.ykskocluk.demo.exception.PaymentProviderException;
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
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SubscriptionService {

    private static final Set<String> PAYMENT_SORTABLE_FIELDS = Set.of("createdAt", "id");
    private static final Set<String> SUBSCRIPTION_SORTABLE_FIELDS = Set.of("createdAt", "id");
    private static final Pattern STUB_CHECKOUT_PATH = Pattern.compile(
            "^/payment/stub/stub-checkout-\\d+-[0-9a-fA-F-]{36}$");

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
    private final AccountReadinessService accountReadinessService;
    private final LegalAcceptanceService legalAcceptanceService;
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher events;

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
                               AccountReadinessService accountReadinessService,
                               LegalAcceptanceService legalAcceptanceService,
                               PlatformTransactionManager transactionManager,
                               ApplicationEventPublisher events) {
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
        this.accountReadinessService = accountReadinessService;
        this.legalAcceptanceService = legalAcceptanceService;
        this.tx = new TransactionTemplate(transactionManager);
        this.events = events;
    }

    @Transactional
    public SubscriptionResponse subscribe(Long studentUserId, SubscriptionCreateRequest request) {
        Subscription subscription = createPendingSubscription(studentUserId, request);
        return subscriptionMapper.toResponse(subscription);
    }

    /**
     * Reserves the PENDING subscription, payment, and checkout legal evidence in one committed
     * transaction, then calls iyzico with no transaction open.
     */
    public SubscriptionCheckoutResponse checkout(Long studentUserId, SubscriptionCheckoutRequest request) {
        Payment payment = tx.execute(status -> {
            Subscription subscription = preparePendingSubscription(studentUserId, request.subscriptionRequest());
            LegalAcceptanceService.CheckoutDocuments documents = legalAcceptanceService.validateCheckout(request);
            savePendingSubscription(subscription);
            Payment pendingPayment = createPendingPayment(subscription);
            legalAcceptanceService.recordCheckoutAcceptances(
                    subscription.getStudent(), subscription, pendingPayment, documents);
            return pendingPayment;
        });

        CheckoutResult checkoutResult = iyzicoClient.initializeCheckout(
                payment.getSubscription().getId(), payment.getId(), payment.getAmount(), payment.getIdempotencyKey());
        validateCheckoutResult(checkoutResult);

        return new SubscriptionCheckoutResponse(
                payment.getSubscription().getId(),
                payment.getId(),
                payment.getSubscription().getStatus(),
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
        return savePendingSubscription(preparePendingSubscription(studentUserId, request));
    }

    private Subscription preparePendingSubscription(Long studentUserId, SubscriptionCreateRequest request) {
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

        accountReadinessService.requireReady(student);

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

        return subscription;
    }

    private Subscription savePendingSubscription(Subscription subscription) {
        try {
            return subscriptionRepository.saveAndFlush(subscription);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_SUBSCRIBED",
                    "Bu koç ile zaten aktif aboneliğiniz var");
        }
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
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Ödeme bulunamadı"));

        if (payment.getType() != PaymentType.CHARGE || payment.getSubscription() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_RELATIONSHIP",
                    "Ödeme abonelik ilişkisi geçersiz");
        }
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

        Instant now = Instant.now();
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setProviderReference(providerReference);
        payment.setSucceededAt(now);
        paymentRepository.saveAndFlush(payment);

        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartAt(now);
        subscription.setEndAt(now.plus(subscription.getPkg().getDurationDays(), ChronoUnit.DAYS));
        subscriptionRepository.saveAndFlush(subscription);
        events.publishEvent(new PurchaseConfirmedEvent(payment.getId(),
                subscription.getStudent().getEmail(), subscription.getStudent().getFullName(),
                subscription.getPkg().getName(), subscription.getCoachProfile().getUser().getEmail(),
                subscription.getCoachProfile().getUser().getFullName(),
                payment.getAmount(), "TRY", now, subscription.getEndAt()));
    }

    /**
     * Business-logic entry point that bypasses signature verification — for internal/test
     * callers that already trust the caller (e.g. unit tests exercising the state machine).
     * Never wire this overload to an HTTP-reachable path; the webhook controller must always
     * go through {@link #processWebhook(IyzicoWebhookRequest, String)}.
     */
    @Transactional
    public IyzicoWebhookResponse processWebhook(IyzicoWebhookRequest request) {
        return applyWebhookOutcome(request);
    }

    @Transactional
    public IyzicoWebhookResponse processWebhook(IyzicoWebhookRequest request, String signatureV3) {
        verifyWebhookSignature(request, signatureV3);
        validateWebhookIdentity(request);
        return applyWebhookOutcome(request);
    }

    private void validateWebhookIdentity(IyzicoWebhookRequest request) {
        if (!"PAYMENT_API".equals(request.iyziEventType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK_EVENT", "Geçersiz webhook olay türü");
        }
        if (request.paymentConversationId() == null
                || !request.paymentId().toString().equals(request.paymentConversationId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_CONVERSATION_MISMATCH",
                    "Webhook ödeme kimliği eşleşmiyor");
        }
        if ("SUCCESS".equalsIgnoreCase(request.status())
                && (request.providerReference() == null || request.providerReference().isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROVIDER_REFERENCE_MISSING",
                    "Başarılı ödeme bildirimi sağlayıcı referansı içermeli");
        }
    }

    /**
     * Fails closed: with no real iyzico integration configured there is no legitimate webhook
     * caller, so an unsigned/unverifiable request must be rejected rather than trusted.
     */
    private void verifyWebhookSignature(IyzicoWebhookRequest request, String signatureV3) {
        if (iyzicoProperties == null || !iyzicoProperties.enabled()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "WEBHOOK_DISABLED", "Iyzico entegrasyonu etkin değil");
        }

        if (signatureV3 == null || signatureV3.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_SIGNATURE", "İmza başlığı eksik");
        }

        String secretKey = iyzicoProperties.secretKey();
        if (secretKey == null || secretKey.isBlank()) {
            secretKey = "";
        }

        String iyziEventType = request.iyziEventType() != null ? request.iyziEventType() : "";
        String paymentIdStr = request.paymentId() != null ? request.paymentId().toString() : "";
        String paymentConversationId = request.paymentConversationId() != null ? request.paymentConversationId() : "";
        String statusStr = request.status() != null ? request.status() : "";

        String data = secretKey + iyziEventType + paymentIdStr + paymentConversationId + statusStr;
        String computedSignature = calculateHmacSha256(data, secretKey);

        boolean matches = MessageDigest.isEqual(
                computedSignature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                signatureV3.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_SIGNATURE", "İmza doğrulanamadı");
        }
    }

    private IyzicoWebhookResponse applyWebhookOutcome(IyzicoWebhookRequest request) {
        if (!"SUCCESS".equalsIgnoreCase(request.status()) && !"FAILURE".equalsIgnoreCase(request.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_WEBHOOK_STATUS", "Geçersiz webhook durumu: " + request.status());
        }

        Payment payment = paymentRepository.findById(request.paymentId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Ödeme bulunamadı"));

        if (payment.getType() != PaymentType.CHARGE || payment.getSubscription() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "WEBHOOK_PAYMENT_MISMATCH",
                    "Webhook bir abonelik tahsilatıyla eşleşmiyor");
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

            completePaymentSuccess(payment, subscription, request.providerReference());
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

    /** Carries the reserve decision out of tx1 into the external call and tx2. */
    private record RefundReserve(Long originalPaymentId, Payment refundPayment, String providerReference,
                                 String idempotencyKey, BigDecimal remainingAfter) {
    }

    /** Result of tx2 — success carries the response; failure carries the provider error message. */
    private record RefundOutcome(boolean success, RefundResponse response) {
    }

    /**
     * Refund. The external iyzico call sits strictly between two committed transactions (CLAUDE.md:
     * no external calls inside a DB transaction): tx1 locks the original payment, validates, and
     * reserves a PENDING refund row (so a concurrent refund attempt sees it as already-reserved);
     * the external call happens with no transaction open; tx2 finalizes the reserved row to
     * SUCCESS or FAILED. A provider failure still commits the FAILED row (audit trail) before the
     * caller is told the refund failed.
     */
    public RefundResponse refund(Long paymentId, BigDecimal refundAmount, String reason) {
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REFUND_AMOUNT", "İade tutarı sıfırdan büyük olmalıdır");
        }

        RefundReserve reserve = tx.execute(status -> reserveRefund(paymentId, refundAmount));

        RefundResult refundResult = iyzicoClient.refund(reserve.providerReference(), refundAmount, reserve.idempotencyKey());
        if (refundResult == null) {
            throw new PaymentProviderException("Iyzico returned no refund response");
        }

        RefundOutcome outcome = tx.execute(status -> finalizeRefund(reserve, refundResult));
        if (!outcome.success()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PROVIDER_REFUND_FAILED",
                    "Ödeme sağlayıcısı iade işlemini tamamlayamadı. Lütfen tekrar deneyin.");
        }
        return outcome.response();
    }

    // --- tx1: lock + validate + reserve ---

    private RefundReserve reserveRefund(Long paymentId, BigDecimal refundAmount) {
        Payment originalPayment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Ödeme bulunamadı"));

        if (originalPayment.getType() != PaymentType.CHARGE || originalPayment.getStatus() != PaymentStatus.SUCCESS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_STATUS", "Sadece başarılı ödemeler iade edilebilir");
        }

        // Already-spoken-for amount = SUCCESS refunds + still-in-flight PENDING reservations.
        // The findByIdForUpdate lock above serializes concurrent reserves on this charge, but a
        // reservation is only visible to the next reserver if we count PENDING too — otherwise two
        // concurrent full-amount refunds each see 0 refunded and both reserve, over-refunding the
        // charge. FAILED refunds released their reservation, so they are excluded.
        List<Payment> existingRefunds = paymentRepository.findBySourcePaymentIdAndStatusIn(
                paymentId, List.of(PaymentStatus.PENDING, PaymentStatus.SUCCESS));
        BigDecimal totalRefunded = existingRefunds.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remainingRefundable = originalPayment.getAmount().subtract(totalRefunded);

        RefundPolicy.Decision policy = RefundPolicy.evaluate(originalPayment.getSucceededAt(), Instant.now(), remainingRefundable);
        if (!policy.eligible()) {
            String code = remainingRefundable.signum() <= 0 ? "PAYMENT_ALREADY_REFUNDED"
                    : originalPayment.getSucceededAt() == null ? "PURCHASE_TIMESTAMP_UNAVAILABLE" : "REFUND_WINDOW_EXPIRED";
            throw new ApiException(HttpStatus.BAD_REQUEST, code, policy.ineligibleReason());
        }

        if (refundAmount.compareTo(remainingRefundable) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EXCEEDS_REFUNDABLE_AMOUNT",
                    String.format("İade tutarı kalan iade edilebilir tutarı (%s TRY) aşamaz", remainingRefundable));
        }

        // Deterministic idempotency key to prevent duplicate refunds
        int refundIndex = Math.toIntExact(paymentRepository.countBySourcePaymentId(paymentId) + 1);
        String refundKey = "refund:" + paymentId + ":" + refundIndex;

        // Reserve a PENDING refund row now, while still holding the row lock, so a concurrent
        // refund attempt on this payment sees this amount as already spoken for.
        Payment refundPayment = new Payment();
        refundPayment.setSubscription(originalPayment.getSubscription());
        refundPayment.setType(PaymentType.REFUND);
        refundPayment.setAmount(refundAmount);
        refundPayment.setStatus(PaymentStatus.PENDING);
        refundPayment.setIdempotencyKey(refundKey);

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

        return new RefundReserve(paymentId, refundPayment, originalPayment.getProviderReference(),
                refundKey, remainingRefundable.subtract(refundAmount));
    }

    // --- tx2: finalize ---

    private RefundOutcome finalizeRefund(RefundReserve reserve, RefundResult refundResult) {
        Payment refundPayment = reserve.refundPayment();

        if (!refundResult.success()) {
            refundPayment.setStatus(PaymentStatus.FAILED);
            paymentRepository.saveAndFlush(refundPayment);
            return new RefundOutcome(false, null);
        }

        refundPayment.setStatus(PaymentStatus.SUCCESS);
        refundPayment.setProviderReference(refundResult.providerReference());
        paymentRepository.saveAndFlush(refundPayment);

        RefundResponse response = new RefundResponse(
                reserve.originalPaymentId(),
                refundPayment.getId(),
                refundPayment.getStatus().name(),
                refundPayment.getAmount(),
                reserve.remainingAfter(),
                "İade işlemi başarıyla gerçekleştirildi"
        );
        return new RefundOutcome(true, response);
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
        validateSort(pageable, PAYMENT_SORTABLE_FIELDS);
        return mapAdminPayments(paymentRepository.findAllAdmin(pageable));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminPaymentResponse> listPayments(PaymentType type, PaymentStatus status,
            Long studentId, Long coachId, Long packageId, Instant from, Instant to, Pageable pageable) {
        validateSort(pageable, PAYMENT_SORTABLE_FIELDS);
        if (from != null && to != null && !to.isAfter(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "Bitiş başlangıçtan sonra olmalı");
        }
        return mapAdminPayments(paymentRepository.searchAdmin(type, status, studentId, coachId, packageId, from, to, pageable));
    }

    private PageResponse<AdminPaymentResponse> mapAdminPayments(Page<Payment> page) {

        // Batch-load refunds for every CHARGE row on this page in one query (avoids N+1).
        List<Long> chargeIds = page.getContent().stream()
                .filter(p -> p.getType() == PaymentType.CHARGE)
                .map(Payment::getId)
                .toList();
        Map<Long, BigDecimal> refundedByChargeId = chargeIds.isEmpty()
                ? Map.of()
                : paymentRepository.findBySourcePaymentIdInAndStatus(chargeIds, PaymentStatus.SUCCESS).stream()
                        .collect(Collectors.groupingBy(
                                p -> p.getSourcePayment().getId(),
                                Collectors.reducing(BigDecimal.ZERO, Payment::getAmount, BigDecimal::add)));

        Page<AdminPaymentResponse> mapped = page.map(payment -> {
            BigDecimal totalRefunded = payment.getType() == PaymentType.CHARGE
                    ? refundedByChargeId.getOrDefault(payment.getId(), BigDecimal.ZERO)
                    : BigDecimal.ZERO;
            BigDecimal remainingRefundable = payment.getType() == PaymentType.CHARGE
                    ? payment.getAmount().subtract(totalRefunded)
                    : BigDecimal.ZERO;
            RefundPolicy.Decision policy = payment.getType() == PaymentType.CHARGE
                    && payment.getStatus() == PaymentStatus.SUCCESS
                    ? RefundPolicy.evaluate(payment.getSucceededAt(), Instant.now(), remainingRefundable)
                    : new RefundPolicy.Decision(false, null, "Yalnızca başarılı tahsilatlar iade edilebilir.");

            return new AdminPaymentResponse(
                    payment.getId(),
                    payment.getSubscription().getId(),
                    payment.getSourcePayment() == null ? null : payment.getSourcePayment().getId(),
                    payment.getSubscription().getStudent().getId(),
                    payment.getSubscription().getStudent().getEmail(),
                    payment.getSubscription().getStudent().getFullName(),
                    payment.getSubscription().getCoachProfile().getId(),
                    payment.getSubscription().getCoachProfile().getUser().getFullName(),
                    payment.getSubscription().getPkg().getId(),
                    payment.getSubscription().getPkg().getName(),
                    payment.getType().name(),
                    payment.getAmount(),
                    payment.getStatus().name(),
                    payment.getProviderReference(),
                    payment.getCreatedAt(),
                    payment.getSucceededAt(),
                    totalRefunded,
                    remainingRefundable,
                    policy.eligible(),
                    policy.deadline(),
                    policy.ineligibleReason()
            );
        });
        return PageResponse.from(mapped);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminSubscriptionResponse> listSubscriptions(Pageable pageable) {
        validateSort(pageable, SUBSCRIPTION_SORTABLE_FIELDS);
        return mapAdminSubscriptions(subscriptionRepository.findAllAdmin(pageable));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminSubscriptionResponse> listSubscriptions(SubscriptionStatus status,
            Long studentId, Long coachId, Long packageId, Instant from, Instant to, Pageable pageable) {
        validateSort(pageable, SUBSCRIPTION_SORTABLE_FIELDS);
        if (from != null && to != null && !to.isAfter(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "Bitiş başlangıçtan sonra olmalı");
        }
        return mapAdminSubscriptions(subscriptionRepository.searchAdmin(status, studentId, coachId, packageId, from, to, pageable));
    }

    private PageResponse<AdminSubscriptionResponse> mapAdminSubscriptions(Page<Subscription> page) {
        Page<AdminSubscriptionResponse> mapped = page.map(sub -> new AdminSubscriptionResponse(
                sub.getId(),
                sub.getStudent().getId(),
                sub.getStudent().getEmail(),
                sub.getStudent().getFullName(),
                sub.getCoachProfile().getId(),
                sub.getCoachProfile().getUser().getFullName(),
                sub.getPkg().getId(),
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

    private void validateSort(Pageable pageable, Set<String> allowed) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SORT_FIELD",
                        "Bu alana göre sıralama yapılamaz: " + order.getProperty());
            }
        }
    }

    private void validateCheckoutResult(CheckoutResult result) {
        if (result == null) {
            throw new PaymentProviderException("Iyzico returned no checkout result");
        }
        try {
            URI uri = URI.create(result.checkoutUrl());
            String host = uri.getHost();
            boolean providerHost = host != null && (host.equals("iyzico.com") || host.endsWith(".iyzico.com")
                    || host.equals("iyzipay.com") || host.endsWith(".iyzipay.com"));
            boolean loopbackHost = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                    || "::1".equals(host);
            boolean localStubUrl = !iyzicoProperties.enabled() && loopbackHost
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && STUB_CHECKOUT_PATH.matcher(uri.getPath()).matches()
                    && uri.getQuery() == null && uri.getFragment() == null;
            boolean trustedProviderUrl = "https".equalsIgnoreCase(uri.getScheme()) && providerHost;
            if (uri.getUserInfo() != null || (!trustedProviderUrl && !localStubUrl)) {
                throw new PaymentProviderException("Iyzico returned an untrusted checkout URL");
            }
        } catch (IllegalArgumentException e) {
            throw new PaymentProviderException("Iyzico returned a malformed checkout URL", e);
        }
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
