package com.ykskocluk.demo.integration;

import com.ykskocluk.demo.config.MeetLinkProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wiring proof for the Phase 6 profile gating — no DB, no full app boot (CI-safe). Registers
 * BOTH client classes and lets {@code @Profile} decide which is active, asserting there is
 * always exactly ONE {@link MeetClient} bean (so the app never fails to start with an ambiguous
 * bean) and that it's the right one per profile.
 *
 * <p>{@code app.meet-link.enabled} deliberately does NOT take part in bean selection: the seam
 * must stay wired while automatic generation is off, so a future Google Meet client is a drop-in
 * replacement. The flag only changes what the active client returns — proven below.
 */
class MeetClientWiringTest {

    private ApplicationContextRunner runner(boolean meetLinkEnabled) {
        return new ApplicationContextRunner()
                .withBean(MeetLinkProperties.class, () -> new MeetLinkProperties(meetLinkEnabled))
                .withUserConfiguration(StubMeetClient.class, JitsiMeetClient.class);
    }

    @Test
    void testProfile_activatesExactlyStubMeetClient() {
        runner(true).withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("test"))
                .run(context -> {
                    assertThat(context.getBeansOfType(MeetClient.class)).hasSize(1);
                    assertThat(context.getBean(MeetClient.class)).isInstanceOf(StubMeetClient.class);
                });
    }

    @Test
    void nonTestProfile_activatesExactlyJitsiMeetClient() {
        runner(true).withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("local"))
                .run(context -> {
                    assertThat(context.getBeansOfType(MeetClient.class)).hasSize(1);
                    assertThat(context.getBean(MeetClient.class)).isInstanceOf(JitsiMeetClient.class);
                });
    }

    @Test
    void defaultProfile_activatesExactlyJitsiMeetClient() {
        // No active profile (production default also has no "test") → real client, single bean.
        runner(true).run(context -> {
            assertThat(context.getBeansOfType(MeetClient.class)).hasSize(1);
            assertThat(context.getBean(MeetClient.class)).isInstanceOf(JitsiMeetClient.class);
        });
    }

    @Test
    void meetLinkDisabled_seamStaysWired_butClientMintsNothing() {
        runner(false).run(context -> {
            // The bean is still there (nothing is conditioned away) …
            assertThat(context.getBeansOfType(MeetClient.class)).hasSize(1);
            assertThat(context.getBean(MeetClient.class)).isInstanceOf(JitsiMeetClient.class);
            // … it just produces no link.
            assertThat(context.getBean(MeetClient.class)
                    .createMeetLink(7L, Instant.now(), Instant.now().plusSeconds(3600))).isNull();
        });
    }
}
