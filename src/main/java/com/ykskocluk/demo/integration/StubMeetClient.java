package com.ykskocluk.demo.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Stub Meet client — returns a deterministic fake link. Real impl in Phase 6. */
@Component
public class StubMeetClient implements MeetClient {

    private static final Logger log = LoggerFactory.getLogger(StubMeetClient.class);

    @Override
    public String createMeetLink(Long sessionId, Instant startTime, Instant endTime) {
        String link = "https://meet.stub.local/session/" + sessionId;
        log.info("[STUB MeetClient] created link {} for session {}", link, sessionId);
        return link;
    }
}
