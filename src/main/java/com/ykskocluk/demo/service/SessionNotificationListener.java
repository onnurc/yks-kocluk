package com.ykskocluk.demo.service;

import com.ykskocluk.demo.integration.MailClient;
import com.ykskocluk.demo.integration.MeetClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * After-commit external side effects for a booked session — the one sanctioned use of
 * {@code @TransactionalEventListener(AFTER_COMMIT)} (CLAUDE.md). Runs after the booking
 * transaction has already committed, so:
 * <ul>
 *   <li>The external Meet/mail calls are NOT inside any DB transaction.</li>
 *   <li>A failure here cannot roll back the booking — it is logged, not propagated.</li>
 * </ul>
 * The only DB write (persisting the Meet link) goes through {@link SessionService#setMeetLink}
 * in its own fresh transaction.
 */
@Component
public class SessionNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(SessionNotificationListener.class);

    private final MeetClient meetClient;
    private final MailClient mailClient;
    private final SessionService sessionService;

    public SessionNotificationListener(MeetClient meetClient, MailClient mailClient,
                                       SessionService sessionService) {
        this.meetClient = meetClient;
        this.mailClient = mailClient;
        this.sessionService = sessionService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSessionBooked(SessionBookedEvent event) {
        try {
            String meetLink = meetClient.createMeetLink(event.sessionId(), event.startTime(), event.endTime());
            sessionService.setMeetLink(event.sessionId(), meetLink);
            mailClient.sendSessionBooked(event.studentEmail(), event.coachName(), event.startTime(), meetLink);
        } catch (Exception e) {
            // Booking is already committed — never let a side-effect failure surface. Log and move on.
            log.error("After-commit notification failed for session {}: {}",
                    event.sessionId(), e.getMessage(), e);
        }
    }
}
