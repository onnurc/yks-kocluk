package com.ykskocluk.demo.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Stub Meet client — returns a deterministic fake link. Active under the {@code test} profile
 * only; {@link JitsiMeetClient} (the real Phase 6 impl) is active under every other profile.
 * Mutually exclusive profiles → exactly one {@link MeetClient} bean, no ambiguity.
 */
@Component
@Profile("test")
public class StubMeetClient implements MeetClient {

    private static final Logger log = LoggerFactory.getLogger(StubMeetClient.class);

    @Override
    public String createMeetLink(Long sessionId, Instant startTime, Instant endTime) {
        String link = "https://meet.stub.local/session/" + sessionId;
        log.info("[STUB MeetClient] meeting created for session {}", sessionId);
        return link;
    }
}
