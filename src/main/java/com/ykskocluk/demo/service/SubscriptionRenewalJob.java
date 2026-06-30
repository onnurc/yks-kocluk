package com.ykskocluk.demo.service;

import com.ykskocluk.demo.config.PaymentProperties;
import com.ykskocluk.demo.dto.SubscriptionEmailView;
import com.ykskocluk.demo.integration.MailClient;
import com.ykskocluk.demo.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Daily auto-renew driver (Phase 8c). Finds the due set and delegates each subscription to
 * {@link SubscriptionBillingService#processDue} — all money logic lives there; this is orchestration
 * only. Each subscription is processed independently (one failure never aborts the batch). The
 * {@code @Scheduled} trigger only fires when scheduling is enabled (non-{@code test} profile, see
 * {@code SchedulingConfig}); tests call {@link #runRenewals} directly.
 */
@Component
public class SubscriptionRenewalJob {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionRenewalJob.class);

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionBillingService billingService;
    private final MailClient mailClient;
    private final PaymentProperties paymentProperties;

    public SubscriptionRenewalJob(SubscriptionRepository subscriptionRepository,
                                  SubscriptionBillingService billingService,
                                  MailClient mailClient,
                                  PaymentProperties paymentProperties) {
        this.subscriptionRepository = subscriptionRepository;
        this.billingService = billingService;
        this.mailClient = mailClient;
        this.paymentProperties = paymentProperties;
    }

    /** Daily at 03:00 Europe/Istanbul (low traffic), overridable via {@code app.payment.renewal-cron}. */
    @Scheduled(cron = "${app.payment.renewal-cron:0 0 3 * * *}", zone = "Europe/Istanbul")
    public void scheduledRun() {
        runRenewals(Instant.now());
    }

    /**
     * Processes every due subscription for {@code now}. Takes {@code now} explicitly (no Clock) so it
     * is deterministic in tests. Due set = ACTIVE past end_at, plus all PAST_DUE (retry/expiry);
     * EXPIRED/CANCELLED are terminal and never selected — a retry-exhausted sub has already become
     * EXPIRED at its final failure (8b), so it cannot linger as PAST_DUE and be re-charged forever.
     */
    public void runRenewals(Instant now) {
        List<Long> dueIds = subscriptionRepository.findDueSubscriptionIds(now);
        log.info("Renewal job: {} due subscription(s) at {}", dueIds.size(), now);

        Map<BillingOutcome, Integer> tally = new EnumMap<>(BillingOutcome.class);
        for (Long id : dueIds) {
            BillingOutcome outcome;
            try {
                outcome = billingService.processDue(id, now);
                tally.merge(outcome, 1, Integer::sum);
            } catch (Exception e) {
                // Isolate failures: one subscription's unexpected error must not stop the rest.
                log.error("Renewal failed for subscription {}: {}", id, e.getMessage(), e);
                continue;
            }
            // After-commit (processDue's tx2 has committed; no tx open here): best-effort email.
            dispatchEmail(outcome, id);
        }
        log.info("Renewal job done: {}", tally);
    }

    /**
     * Best-effort email for one processed subscription, keyed to the billing outcome. Wrapped in its
     * own try/catch so a mail failure (on top of the client's own swallow) can never affect billing
     * state or the rest of the batch. SKIPPED outcomes send nothing (and skip the lookup).
     */
    private void dispatchEmail(BillingOutcome outcome, Long subscriptionId) {
        if (outcome == BillingOutcome.SKIPPED_ALREADY_PROCESSED || outcome == BillingOutcome.SKIPPED_NOT_DUE) {
            return;
        }
        try {
            SubscriptionEmailView v = subscriptionRepository.findEmailViewById(subscriptionId);
            if (v == null) {
                return;
            }
            switch (outcome) {
                case CHARGED_SUCCESS ->
                        mailClient.sendRenewalSucceeded(v.studentEmail(), v.coachName(), v.endAt(), v.amount());
                case CHARGED_FAILED_PAST_DUE ->
                        mailClient.sendPaymentFailed(v.studentEmail(), v.coachName(),
                                v.failedChargeCount(), paymentProperties.retryDays());
                case CHARGED_FAILED_EXPIRED, EXPIRED_NO_RENEW ->
                        mailClient.sendSubscriptionExpired(v.studentEmail(), v.coachName());
                default -> { /* no email */ }
            }
        } catch (Exception e) {
            log.error("Email dispatch failed for subscription {} (outcome {}): {}",
                    subscriptionId, outcome, e.getMessage(), e);
        }
    }
}
