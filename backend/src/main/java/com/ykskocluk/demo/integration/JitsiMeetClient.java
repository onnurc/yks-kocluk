package com.ykskocluk.demo.integration;

import com.ykskocluk.demo.config.MeetLinkProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Real {@link MeetClient} (Phase 6) — public meet.jit.si, the Jitsi fallback chosen in Phase 0.5
 * (Google Meet needs a Workspace account). Active under every non-{@code test} profile; the
 * {@link StubMeetClient} stays active under {@code test} (mutually exclusive profiles → exactly
 * one bean, no ambiguity).
 *
 * <p>meet.jit.si needs no account, key, or credentials: a room <em>is</em> its URL, created when
 * the first participant opens it. So this generates the URL <strong>locally</strong> — no HTTP
 * call, no I/O — and the method is total (no checked exceptions for the after-commit seam to
 * handle). The room id is a v4 {@link UUID}: this is a minors-facing platform, so the id MUST be
 * unguessable (a sequential/guessable id would let an uninvited person join).
 *
 * <p><strong>Gated by {@code app.meet-link.enabled} (default false).</strong> While off this
 * mints nothing and returns {@code null} — the coach shares a Google Meet link over chat instead.
 * The bean stays registered on purpose rather than being conditioned away: the seam is what a
 * future Google Meet client plugs into, and {@code SessionNotificationListener} keeps a single,
 * non-optional {@link MeetClient} dependency either way.
 */
@Component
@Profile("!test")
public class JitsiMeetClient implements MeetClient {

    private static final Logger log = LoggerFactory.getLogger(JitsiMeetClient.class);

    private static final String BASE_URL = "https://meet.jit.si";

    private final MeetLinkProperties properties;

    public JitsiMeetClient(MeetLinkProperties properties) {
        this.properties = properties;
    }

    @Override
    public String createMeetLink(Long sessionId, Instant startTime, Instant endTime) {
        if (!properties.enabled()) {
            log.debug("[Jitsi MeetClient] automatic meet links are disabled; none created for session {}",
                    sessionId);
            return null;
        }
        // UUID v4 = the unguessable entropy source. The "yks-" prefix is readability only.
        String link = BASE_URL + "/yks-" + UUID.randomUUID();
        log.info("[Jitsi MeetClient] meeting created for session {}", sessionId);
        return link;
    }
}
