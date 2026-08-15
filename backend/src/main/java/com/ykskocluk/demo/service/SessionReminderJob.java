package com.ykskocluk.demo.service;

import com.ykskocluk.demo.config.SessionReminderProperties;
import com.ykskocluk.demo.dto.SessionReminderView;
import com.ykskocluk.demo.integration.MailClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Session-reminder driver (mirrors {@link SubscriptionRenewalJob}'s shape). Every run picks up
 * PLANNED sessions that have crossed into the reminder lead window and haven't been claimed yet
 * ({@link SessionService#findReminderCandidates}), atomically claims each one
 * ({@link SessionService#claimReminder} — the idempotency guard, see {@code reminder_sent_at} on
 * {@code Session}), then sends the mail after the claim has committed. One session's failure never
 * aborts the batch. The frequent poll interval (default every 15 min, vs. the renewal job's daily
 * run) is what keeps the actual lead time close to {@code leadTime} without needing precise
 * per-session timers — a session simply gets caught on whichever run first sees it inside the
 * window, so the job is self-healing after downtime instead of needing exact scheduling.
 */
@Component
public class SessionReminderJob {

    private static final Logger log = LoggerFactory.getLogger(SessionReminderJob.class);

    private final SessionService sessionService;
    private final MailClient mailClient;
    private final SessionReminderProperties properties;

    public SessionReminderJob(SessionService sessionService, MailClient mailClient,
                              SessionReminderProperties properties) {
        this.sessionService = sessionService;
        this.mailClient = mailClient;
        this.properties = properties;
    }

    /** Every 15 minutes, overridable via {@code app.session-reminder.cron}. */
    @Scheduled(cron = "${app.session-reminder.cron:0 */15 * * * *}", zone = "Europe/Istanbul")
    public void scheduledRun() {
        runReminders(Instant.now());
    }

    /** Takes {@code now} explicitly (no Clock) so it is deterministic in tests. */
    public void runReminders(Instant now) {
        Instant horizon = now.plus(properties.leadTime());
        List<SessionReminderView> candidates = sessionService.findReminderCandidates(now, horizon);
        log.info("Reminder job: {} candidate session(s) at {}", candidates.size(), now);

        int sent = 0;
        for (SessionReminderView view : candidates) {
            try {
                if (!sessionService.claimReminder(view.sessionId(), now)) {
                    continue; // already claimed by a previous/overlapping run
                }
                mailClient.sendSessionReminder(view.studentEmail(), view.coachName(), view.startTime(),
                        view.meetLink());
                sent++;
            } catch (Exception e) {
                // Isolate failures: one session's unexpected error must not stop the rest.
                log.error("Reminder failed for session {}: {}", view.sessionId(), e.getMessage(), e);
            }
        }
        log.info("Reminder job done: {} sent", sent);
    }
}
