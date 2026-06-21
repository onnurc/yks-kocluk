package com.ykskocluk.demo.integration;

import java.time.Instant;

/**
 * Video-meeting provider (Google Meet, with a Jitsi fallback — see Phase 0.5 findings).
 * One of the four sanctioned external-service interfaces (stub-first). The real
 * implementation lands in Phase 6; until then {@code StubMeetClient} returns a fake link.
 */
public interface MeetClient {

    /** Returns a join link for the session's time window. */
    String createMeetLink(Long sessionId, Instant startTime, Instant endTime);
}
