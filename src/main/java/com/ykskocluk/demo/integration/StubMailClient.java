package com.ykskocluk.demo.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Stub mail client — logs instead of sending. Real impl in Phase 7. */
@Component
public class StubMailClient implements MailClient {

    private static final Logger log = LoggerFactory.getLogger(StubMailClient.class);

    @Override
    public void sendSessionBooked(String toEmail, String coachName, Instant startTime, String meetLink) {
        log.info("[STUB MailClient] session-booked mail to {} (coach {}, start {}, link {})",
                toEmail, coachName, startTime, meetLink);
    }
}
