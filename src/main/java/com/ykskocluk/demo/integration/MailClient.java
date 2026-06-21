package com.ykskocluk.demo.integration;

import java.time.Instant;

/**
 * Transactional email provider (Resend). One of the four sanctioned external-service
 * interfaces (stub-first). The real implementation lands in Phase 7; until then
 * {@code StubMailClient} just logs.
 */
public interface MailClient {

    void sendSessionBooked(String toEmail, String coachName, Instant startTime, String meetLink);
}
