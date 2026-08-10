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
}
