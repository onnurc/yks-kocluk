package com.ykskocluk.demo.service;

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

    public SubscriptionRenewalJob(SubscriptionRepository subscriptionRepository,
                                  SubscriptionBillingService billingService) {
        this.subscriptionRepository = subscriptionRepository;
        this.billingService = billingService;
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
            try {
                BillingOutcome outcome = billingService.processDue(id, now);
                tally.merge(outcome, 1, Integer::sum);
            } catch (Exception e) {
                // Isolate failures: one subscription's unexpected error must not stop the rest.
                log.error("Renewal failed for subscription {}: {}", id, e.getMessage(), e);
            }
        }
        log.info("Renewal job done: {}", tally);
    }
}
