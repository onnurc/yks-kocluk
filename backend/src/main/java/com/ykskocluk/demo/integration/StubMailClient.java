package com.ykskocluk.demo.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Stub mail client — logs instead of sending. Active under the {@code test} profile only;
 * {@link ResendMailClient} (the real Phase 7 impl) is active under every other profile.
 * Mutually exclusive profiles → exactly one {@link MailClient} bean, no ambiguity.
 */
@Component
@Profile("test")
public class StubMailClient implements MailClient {

    private static final Logger log = LoggerFactory.getLogger(StubMailClient.class);

    @Override
    public void sendSessionBooked(String toEmail, String coachName, Instant startTime, String meetLink) {
        log.info("[STUB MailClient] session-booked mail to {} (coach {}, start {}, link {})",
                toEmail, coachName, startTime, meetLink);
    }
}
