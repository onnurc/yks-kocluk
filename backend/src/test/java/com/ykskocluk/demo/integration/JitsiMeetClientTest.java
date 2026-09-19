package com.ykskocluk.demo.integration;

import com.ykskocluk.demo.config.MeetLinkProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit test for {@link JitsiMeetClient} — pure local generation, no Spring context, no I/O.
 * Proves two contracts: while {@code app.meet-link.enabled} is off nothing is minted at all,
 * and while on the room id is a valid, unguessable v4 UUID (the child-safety requirement).
 */
class JitsiMeetClientTest {

    private static final String PREFIX = "https://meet.jit.si/yks-";

    private final JitsiMeetClient enabled = new JitsiMeetClient(new MeetLinkProperties(true));
    private final JitsiMeetClient disabled = new JitsiMeetClient(new MeetLinkProperties(false));

    @Test
    void disabled_generatesNothing() {
        assertThat(disabled.createMeetLink(1L, Instant.now(), Instant.now().plusSeconds(3600))).isNull();
    }

    @Test
    void link_hasJitsiBaseAndValidUuidRoomId() {
        String link = enabled.createMeetLink(1L, Instant.now(), Instant.now().plusSeconds(3600));

        assertThat(link).startsWith("https://meet.jit.si/");
        assertThat(link).startsWith(PREFIX);

        // The room id after the prefix must parse as a real UUID (fromString throws otherwise).
        String roomId = link.substring(PREFIX.length());
        assertThatCode(() -> UUID.fromString(roomId)).doesNotThrowAnyException();
        // v4 UUIDs report version 4 — confirms randomUUID() is the entropy source.
        assertThat(UUID.fromString(roomId).version()).isEqualTo(4);
    }

    @Test
    void successiveCalls_produceDifferentRoomIds() {
        String a = enabled.createMeetLink(1L, Instant.now(), Instant.now().plusSeconds(3600));
        String b = enabled.createMeetLink(1L, Instant.now(), Instant.now().plusSeconds(3600));

        // Same session args, different rooms → unguessable, not derived from session id.
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void createMeetLink_isTotal_neverThrows() {
        assertThatCode(() -> enabled.createMeetLink(null, null, null)).doesNotThrowAnyException();
        assertThatCode(() -> disabled.createMeetLink(null, null, null)).doesNotThrowAnyException();
    }
}
