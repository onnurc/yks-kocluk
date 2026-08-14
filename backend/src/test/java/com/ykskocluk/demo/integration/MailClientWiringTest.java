package com.ykskocluk.demo.integration;

import com.ykskocluk.demo.config.ResendProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wiring proof for the Phase 7 profile gating (mirrors {@code MeetClientWiringTest}) — no DB, no
 * full app boot. Registers BOTH mail clients and the deps {@link ResendMailClient} needs, then
 * lets {@code @Profile} decide: there must always be exactly ONE {@link MailClient} bean (no
 * ambiguous-bean startup failure), and it must be the right one per profile.
 */
class MailClientWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(ResendProperties.class, () -> new ResendProperties("re_test_key", "onboarding@resend.dev", null))
            .withBean("resendRestClientBuilder", RestClient.Builder.class, RestClient::builder)
            .withUserConfiguration(StubMailClient.class, ResendMailClient.class);

    @Test
    void testProfile_activatesExactlyStubMailClient() {
        runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("test"))
                .run(context -> {
                    assertThat(context.getBeansOfType(MailClient.class)).hasSize(1);
                    assertThat(context.getBean(MailClient.class)).isInstanceOf(StubMailClient.class);
                });
    }

    @Test
    void nonTestProfile_activatesExactlyResendMailClient() {
        runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("local"))
                .run(context -> {
                    assertThat(context.getBeansOfType(MailClient.class)).hasSize(1);
                    assertThat(context.getBean(MailClient.class)).isInstanceOf(ResendMailClient.class);
                });
    }

    @Test
    void defaultProfile_activatesExactlyResendMailClient() {
        runner.run(context -> {
            assertThat(context.getBeansOfType(MailClient.class)).hasSize(1);
            assertThat(context.getBean(MailClient.class)).isInstanceOf(ResendMailClient.class);
        });
    }
}
