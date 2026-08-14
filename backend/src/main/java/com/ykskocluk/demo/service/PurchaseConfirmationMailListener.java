package com.ykskocluk.demo.service;

import com.ykskocluk.demo.integration.MailClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PurchaseConfirmationMailListener {
    private final MailClient mailClient;

    public PurchaseConfirmationMailListener(MailClient mailClient) {
        this.mailClient = mailClient;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPurchaseConfirmed(PurchaseConfirmedEvent event) {
        mailClient.sendPurchaseConfirmed(event.recipientEmail(), event.studentName(), event.packageName(),
                event.coachName(), event.amount(), event.currency(), event.purchasedAt(), event.periodEndAt());
        mailClient.sendPurchaseConfirmedToCoach(event.coachEmail(), event.coachName(), event.studentName(),
                event.packageName(), event.purchasedAt());
    }
}
