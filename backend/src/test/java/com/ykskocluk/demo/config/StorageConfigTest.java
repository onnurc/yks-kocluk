package com.ykskocluk.demo.config;

import com.ykskocluk.demo.storage.StorageService;
import com.ykskocluk.demo.storage.StubStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;

class StorageConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(StorageConfig.class, StubStorageService.class);

    @Test void disabledStartsWithoutCredentialsAndUsesStub() {
        contextRunner.withPropertyValues("app.r2.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(StorageService.class);
            assertThat(context.getBean(StorageService.class)).isInstanceOf(StubStorageService.class);
            assertThat(context).doesNotHaveBean(S3Client.class);
        });
    }

    @Test void enabledWithoutRequiredConfigurationFailsCleanly() {
        contextRunner.withPropertyValues("app.r2.enabled=true").run(context ->
                assertThat(context).hasFailed());
    }

    @Test void productionR2FailsStartupWithLocalMediaBaseUrl() {
        contextRunner.withPropertyValues(
                "app.r2.enabled=true",
                "app.r2.account-id=account",
                "app.r2.access-key-id=key",
                "app.r2.secret-access-key=secret",
                "app.r2.bucket=bucket",
                "app.r2.endpoint=https://account.r2.cloudflarestorage.com",
                "app.media.public-base-url=http://localhost:8080"
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage(
                    "MEDIA_PUBLIC_BASE_URL must be a non-local absolute HTTPS origin when R2 is enabled outside local development");
        });
    }

    @Test void localProfileR2StartsWithLocalMediaBaseUrl() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
                .withPropertyValues(
                        "app.r2.enabled=true",
                        "app.r2.account-id=account",
                        "app.r2.access-key-id=key",
                        "app.r2.secret-access-key=secret",
                        "app.r2.bucket=bucket",
                        "app.r2.endpoint=https://account.r2.cloudflarestorage.com",
                        "app.media.public-base-url=http://localhost:8080"
                ).run(context -> assertThat(context).hasNotFailed());
    }
}
