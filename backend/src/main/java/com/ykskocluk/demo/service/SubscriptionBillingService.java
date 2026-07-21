package com.ykskocluk.demo.service;

import com.ykskocluk.demo.config.PaymentProperties;
import com.ykskocluk.demo.entity.Payment;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.enums.PaymentStatus;
import com.ykskocluk.demo.enums.PaymentType;
import com.ykskocluk.demo.enums.SubscriptionStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.mapper.SubscriptionMapper;
import com.ykskocluk.demo.integration.ChargeResult;
import com.ykskocluk.demo.integration.IyzicoClient;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.PaymentRepository;
import com.ykskocluk.demo.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * The auto-renew billing core (Phase 8b). Charges saved cards on renewal, handles the failed-payment
 * grace/retry/expiry lifecycle, and cancellation — all behind the {@link IyzicoClient} seam (stub in
 * Stage 1). The scheduled job (8c) and emails (8d) are layered on top; this class is callable directly.
 *
 * <p><strong>Transaction boundaries</strong> ({@link TransactionTemplate}, not {@code @Transactional}
 * self-invocation): the external charge sits strictly <em>between</em> two committed transactions.
 * <pre>
 *   tx1  reserve a PENDING Payment with a UNIQUE idempotency key (or resume a stale one)
 *   ---  iyzicoClient.charge(...)            // NO transaction open
 *   tx2  finalize the Payment + advance the subscription
 * </pre>
 * A crash between tx1 and the charge leaves the subscription un-advanced and a PENDING row behind;
 * the next run <em>resumes</em> that row, re-charging with the same key (provider-idempotent → never a
 * double charge), then finalizes. The {@code UNIQUE(idempotency_key)} is the guard against two
 * concurrent runs charging the same window.
 */
@Service
public class SubscriptionBillingService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionBillingService.class);
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final CoachProfileRepository coachProfileRepository;
    private final IyzicoClient iyzicoClient;
    private final PaymentProperties paymentProperties;
    private final SubscriptionMapper subscriptionMapper;
    private final TransactionTemplate tx;

    public SubscriptionBillingService(SubscriptionRepository subscriptionRepository,
                                      PaymentRepository paymentRepository,
                                      CoachProfileRepository coachProfileRepository,
                                      IyzicoClient iyzicoClient,
                                      PaymentProperties paymentProperties,
                                      SubscriptionMapper subscriptionMapper,
                                      PlatformTransactionManager transactionManager) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentRepository = paymentRepository;
        this.coachProfileRepository = coachProfileRepository;
        this.iyzicoClient = iyzicoClient;
        this.paymentProperties = paymentProperties;
        this.subscriptionMapper = subscriptionMapper;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Carries the reserve decision out of tx1: either short-circuit, or a row to charge. */
    private record Reserve(BillingOutcome shortCircuit, Long paymentId,
                           String savedCardToken, BigDecimal amount, String idempotencyKey) {
        static Reserve shortCircuit(BillingOutcome outcome) {
            return new Reserve(outcome, null, null, null, null);
        }
        static Reserve charge(Long paymentId, String token, BigDecimal amount, String key) {
            return new Reserve(null, paymentId, token, amount, key);
        }
    }

    /**
     * Processes one due/PAST_DUE subscription for the given {@code now}. Safe to call concurrently
     * for the same subscription — exactly one attempt charges per window.
     */
    public BillingOutcome processDue(Long subscriptionId, Instant now) {
        Reserve reserve;
        try {
            reserve = tx.execute(status -> reserve(subscriptionId, now));
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(idempotency_key) collision — another run reserved this window first.
            return BillingOutcome.SKIPPED_ALREADY_PROCESSED;
        }
        if (reserve.shortCircuit() != null) {
            return reserve.shortCircuit();
        }

        // External call — outside any transaction. Same key whether fresh or resumed → idempotent.
        ChargeResult result = iyzicoClient.charge(reserve.savedCardToken(), reserve.amount(), reserve.idempotencyKey());

        return tx.execute(status -> finalizeCharge(subscriptionId, reserve.paymentId(), result, now));
    }

    // --- tx1: decide + reserve ---

    private Reserve reserve(Long subscriptionId, Instant now) {
        Subscription sub = subscriptionRepository.findById(subscriptionId).orElse(null);
        if (sub == null) {
            return Reserve.shortCircuit(BillingOutcome.SKIPPED_NOT_DUE);
        }

        return switch (decideBranch(sub, now)) {
            case NOT_DUE -> Reserve.shortCircuit(BillingOutcome.SKIPPED_NOT_DUE);
            case EXPIRE_NO_RENEW -> {
                expire(sub);   // cancelled (auto-renew off) and due → EXPIRE without charging
                yield Reserve.shortCircuit(BillingOutcome.EXPIRED_NO_RENEW);
            }
            case CHARGE -> {
                String todayKey = idempotencyKey(sub.getId(), now);
                Payment pending = paymentRepository
                        .findFirstBySubscriptionIdAndStatus(sub.getId(), PaymentStatus.PENDING).orElse(null);
                if (pending != null) {
                    if (pending.getIdempotencyKey().equals(todayKey)) {
                        // A concurrent run already reserved today's attempt and is mid-charge — back off.
                        yield Reserve.shortCircuit(BillingOutcome.SKIPPED_ALREADY_PROCESSED);
                    }
                    // A PENDING from a PRIOR window = a crash leftover → resume it with its own key.
                    yield Reserve.charge(pending.getId(), sub.getSavedCardToken(),
                            pending.getAmount(), pending.getIdempotencyKey());
                }
                // Reserve a fresh attempt — UNIQUE(idempotency_key) collision (lost the race) → SKIPPED.
                Payment fresh = paymentRepository.saveAndFlush(newPending(sub, todayKey));
                yield Reserve.charge(fresh.getId(), sub.getSavedCardToken(),
                        fresh.getAmount(), fresh.getIdempotencyKey());
            }
        };
    }

    private enum Branch { NOT_DUE, CHARGE, EXPIRE_NO_RENEW }

    private Branch decideBranch(Subscription sub, Instant now) {
        boolean due = !sub.getEndAt().isAfter(now);   // end_at <= now
        return switch (sub.getStatus()) {
            case PENDING_PAYMENT -> Branch.NOT_DUE;
            case ACTIVE -> !due ? Branch.NOT_DUE : (sub.isAutoRenew() ? Branch.CHARGE : Branch.EXPIRE_NO_RENEW);
            // PAST_DUE is already overdue: retry while auto-renew is on, else stop and expire.
            case PAST_DUE -> sub.isAutoRenew() ? Branch.CHARGE : Branch.EXPIRE_NO_RENEW;
            case EXPIRED, CANCELLED, TERMINATED -> Branch.NOT_DUE;
        };
    }

    /** One attempt per subscription per Istanbul calendar day — the UNIQUE guard. */
    private String idempotencyKey(Long subscriptionId, Instant now) {
        return "charge:" + subscriptionId + ":" + now.atZone(ISTANBUL).toLocalDate();
    }

    private Payment newPending(Subscription sub, String idempotencyKey) {
        BigDecimal amount = sub.getPkg().getPrice();
        BigDecimal rate = paymentProperties.commissionRate();
        BigDecimal commission = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);

        Payment p = new Payment();
        p.setSubscription(sub);
        p.setType(PaymentType.CHARGE);
        p.setStatus(PaymentStatus.PENDING);
        p.setAmount(amount);
        p.setIdempotencyKey(idempotencyKey);
        // Commission snapshot, frozen at reserve time (amount & rate don't change before finalize).
        p.setCommissionRate(rate);
        p.setCommissionAmount(commission);
        p.setCoachPayoutAmount(amount.subtract(commission));
        return p;
    }

    // --- tx2: finalize ---

    private BillingOutcome finalizeCharge(Long subscriptionId, Long paymentId, ChargeResult result, Instant now) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        if (payment.getStatus() != PaymentStatus.PENDING) {
            // A concurrent run already finalized this row (resume race) — nothing to do.
            return BillingOutcome.SKIPPED_ALREADY_PROCESSED;
        }
        Subscription sub = subscriptionRepository.findById(subscriptionId).orElseThrow();
        sub.setLastChargeAttemptAt(now);

        if (result.success()) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setProviderReference(result.providerReference());
            sub.setEndAt(sub.getEndAt().plus(sub.getPkg().getDurationDays(), ChronoUnit.DAYS));
            sub.setStatus(SubscriptionStatus.ACTIVE);
            sub.setFailedChargeCount(0);
            return BillingOutcome.CHARGED_SUCCESS;
        }

        payment.setStatus(PaymentStatus.FAILED);
        int fails = sub.getFailedChargeCount() + 1;
        sub.setFailedChargeCount(fails);
        if (fails >= paymentProperties.retryDays()) {
            expire(sub);   // retries exhausted
            return BillingOutcome.CHARGED_FAILED_EXPIRED;
        }
        sub.setStatus(SubscriptionStatus.PAST_DUE);   // grace: access stays open
        return BillingOutcome.CHARGED_FAILED_PAST_DUE;
    }

    /** Terminal expiry: cut access and atomically release the coach's capacity (floored at 0). */
    private void expire(Subscription sub) {
        sub.setStatus(SubscriptionStatus.EXPIRED);
        coachProfileRepository.decrementActiveStudentCount(sub.getCoachProfile().getId());
    }

    /**
     * Releases subscriptions stuck in PENDING_PAYMENT past the checkout timeout — either the
     * payment webhook never arrived (abandoned checkout) or it arrived as FAILURE, which leaves the
     * subscription PENDING_PAYMENT. Without this they permanently block the student from
     * re-subscribing to that coach (ALREADY_SUBSCRIBED), and the renewal job never selects them.
     *
     * <p>Each subscription is expired in its own transaction (one failure never aborts the batch),
     * re-checking status + age inside the tx to avoid racing a webhook that just activated it. The
     * PENDING charge row is finalized to FAILED so a late webhook is treated as idempotent instead
     * of reactivating an expired subscription. No capacity is released — PENDING_PAYMENT never
     * consumed a seat (capacity is taken only on activation). Returns the number expired.
     */
    public int expireStalePendingCheckouts(Instant now) {
        Instant cutoff = now.minus(paymentProperties.pendingCheckoutTimeoutMinutes(), ChronoUnit.MINUTES);
        List<Long> staleIds = subscriptionRepository.findStalePendingCheckoutIds(cutoff);
        int expired = 0;
        for (Long id : staleIds) {
            try {
                if (Boolean.TRUE.equals(tx.execute(status -> expireOnePendingCheckout(id, cutoff)))) {
                    expired++;
                }
            } catch (Exception e) {
                log.error("Failed to expire stale pending checkout {}: {}", id, e.getMessage(), e);
            }
        }
        if (expired > 0) {
            log.info("Expired {} stale PENDING_PAYMENT subscription(s) at {}", expired, now);
        }
        return expired;
    }

    /** tx: re-verify (guards a just-arrived webhook) then EXPIRE the sub and FAIL its PENDING charge. */
    private boolean expireOnePendingCheckout(Long subscriptionId, Instant cutoff) {
        Subscription sub = subscriptionRepository.findById(subscriptionId).orElse(null);
        if (sub == null
                || sub.getStatus() != SubscriptionStatus.PENDING_PAYMENT
                || sub.getCreatedAt().isAfter(cutoff)) {
            return false;   // activated, already handled, or no longer stale — leave it
        }
        sub.setStatus(SubscriptionStatus.EXPIRED);
        for (Payment p : paymentRepository.findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId)) {
            if (p.getStatus() == PaymentStatus.PENDING) {
                p.setStatus(PaymentStatus.FAILED);
            }
        }
        return true;
    }

    /**
     * Student cancels: auto-renew off + cancelledAt stamped, but the subscription stays live until
     * end_at (no refund, no immediate cutoff). It EXPIRES at end_at on the next renewal run.
     *
     * <p><strong>Idempotent:</strong> cancelling an already-cancelled (auto-renew off) live sub is a
     * no-op — current state is returned, {@code cancelledAt} is not re-stamped, and
     * {@code newlyCancelled} is false so the controller sends no second email. EXPIRED/CANCELLED →
     * {@code SUBSCRIPTION_NOT_CANCELLABLE}; not the owner → {@code NOT_SUBSCRIPTION_OWNER}.
     */
    public CancelResult cancel(Long subscriptionId, Long studentUserId) {
        return tx.execute(status -> {
            Subscription sub = subscriptionRepository.findById(subscriptionId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SUBSCRIPTION_NOT_FOUND",
                            "Abonelik bulunamadı"));
            if (!sub.getStudent().getId().equals(studentUserId)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "NOT_SUBSCRIPTION_OWNER",
                        "Bu abonelik size ait değil");
            }
            if (sub.getStatus() != SubscriptionStatus.ACTIVE && sub.getStatus() != SubscriptionStatus.PAST_DUE) {
                throw new ApiException(HttpStatus.CONFLICT, "SUBSCRIPTION_NOT_CANCELLABLE",
                        "Yalnızca aktif abonelikler iptal edilebilir");
            }
            boolean newlyCancelled = sub.isAutoRenew();   // was on → this call turns it off
            if (newlyCancelled) {
                sub.setAutoRenew(false);
                sub.setCancelledAt(Instant.now());
                log.info("Subscription {} cancelled by student {} — live until {}",
                        subscriptionId, studentUserId, sub.getEndAt());
            }
            return new CancelResult(subscriptionMapper.toResponse(sub), newlyCancelled, sub.getStudent().getEmail());
        });
    }
}
