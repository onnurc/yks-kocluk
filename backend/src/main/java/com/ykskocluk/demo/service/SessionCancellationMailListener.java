package com.ykskocluk.demo.service;

import com.ykskocluk.demo.integration.MailClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class SessionCancellationMailListener {
    private final MailClient mailClient;

    public SessionCancellationMailListener(MailClient mailClient) {
        this.mailClient = mailClient;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSessionCancelled(SessionCancelledEvent event) {
        mailClient.sendSessionCancelled(event.studentEmail(), event.coachName(), event.startTime(), event.late());
        mailClient.sendSessionCancelledToCoach(event.coachEmail(), event.coachName(), event.studentName(),
                event.startTime(), event.late());
    }
}
