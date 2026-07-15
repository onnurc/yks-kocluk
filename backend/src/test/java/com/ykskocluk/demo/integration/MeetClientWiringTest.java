package com.ykskocluk.demo.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wiring proof for the Phase 6 profile gating — no DB, no full app boot (CI-safe). Registers
 * BOTH client classes and lets {@code @Profile} decide which is active, asserting there is
 * always exactly ONE {@link MeetClient} bean (so the app never fails to start with an ambiguous
 * bean) and that it's the right one per profile.
 */
class MeetClientWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(StubMeetClient.class, JitsiMeetClient.class);

    @Test
    void testProfile_activatesExactlyStubMeetClient() {
        runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("test"))
                .run(context -> {
                    assertThat(context.getBeansOfType(MeetClient.class)).hasSize(1);
                    assertThat(context.getBean(MeetClient.class)).isInstanceOf(StubMeetClient.class);
                });
    }

    @Test
    void nonTestProfile_activatesExactlyJitsiMeetClient() {
        runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("local"))
                .run(context -> {
                    assertThat(context.getBeansOfType(MeetClient.class)).hasSize(1);
                    assertThat(context.getBean(MeetClient.class)).isInstanceOf(JitsiMeetClient.class);
                });
    }

    @Test
    void defaultProfile_activatesExactlyJitsiMeetClient() {
        // No active profile (production default also has no "test") → real client, single bean.
        runner.run(context -> {
            assertThat(context.getBeansOfType(MeetClient.class)).hasSize(1);
            assertThat(context.getBean(MeetClient.class)).isInstanceOf(JitsiMeetClient.class);
        });
    }
}
