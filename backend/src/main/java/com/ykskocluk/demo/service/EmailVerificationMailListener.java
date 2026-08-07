package com.ykskocluk.demo.service;

import com.ykskocluk.demo.integration.MailClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EmailVerificationMailListener {
    private final MailClient mailClient;

    public EmailVerificationMailListener(MailClient mailClient) {
        this.mailClient = mailClient;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVerificationRequested(EmailVerificationMailEvent event) {
        mailClient.sendEmailVerification(event.email(), event.code());
    }
}
