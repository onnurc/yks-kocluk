package com.ykskocluk.demo.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the {@code RESEND_FROM} fail-fast guard in {@link ResendConfig} — mirrors
 * {@code StorageConfigTest}'s shape (no full Spring Boot app, just the one {@code @Configuration}
 * class + the auto-configuration that makes {@code @EnableConfigurationProperties} work).
 */
class ResendConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(ResendConfig.class)
            .withPropertyValues("app.resend.api-key=re_test_key");

    @Test
    void bareProfile_missingResendFrom_failsFast() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
        });
    }

    @Test
    void bareProfile_withResendFrom_startsFine() {
        runner.withPropertyValues("RESEND_FROM=noreply@uniformakademi.com")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void localProfile_missingResendFrom_startsFine() {
        runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("local"))
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void stubProfile_missingResendFrom_startsFine() {
        runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("stub"))
                .run(context -> assertThat(context).hasNotFailed());
    }
}
