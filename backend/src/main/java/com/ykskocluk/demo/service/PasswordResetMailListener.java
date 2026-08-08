package com.ykskocluk.demo.service;

import com.ykskocluk.demo.integration.MailClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PasswordResetMailListener {
    private final MailClient mailClient;

    public PasswordResetMailListener(MailClient mailClient) { this.mailClient = mailClient; }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(PasswordResetMailEvent event) {
        mailClient.sendPasswordReset(event.email(), event.resetLink());
    }
}
