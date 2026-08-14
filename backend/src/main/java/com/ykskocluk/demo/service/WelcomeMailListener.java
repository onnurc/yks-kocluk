package com.ykskocluk.demo.service;

import com.ykskocluk.demo.integration.MailClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WelcomeMailListener {
    private final MailClient mailClient;

    public WelcomeMailListener(MailClient mailClient) {
        this.mailClient = mailClient;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWelcome(WelcomeMailEvent event) {
        mailClient.sendWelcome(event.email(), event.fullName());
    }
}
