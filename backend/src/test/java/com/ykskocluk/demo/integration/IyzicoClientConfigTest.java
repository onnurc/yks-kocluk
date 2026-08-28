package com.ykskocluk.demo.integration;

import com.ykskocluk.demo.config.IyzicoProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class IyzicoClientConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(StubIyzicoClient.class, RealIyzicoClient.class);

    @Test
    void whenIyzicoDisabled_inLocalProfile_activatesStubClient() {
        runner.withPropertyValues("payments.iyzico.enabled=false")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
                .withBean(IyzicoProperties.class, () -> new IyzicoProperties(false, "sandbox", null, null, null, null))
                .run(context -> {
                    assertThat(context).hasSingleBean(IyzicoClient.class);
                    assertThat(context.getBean(IyzicoClient.class)).isInstanceOf(StubIyzicoClient.class);
                });
    }

    @Test
    void whenIyzicoDisabled_outsideLocalProfiles_doesNotExposeStubClient() {
        runner.withPropertyValues("payments.iyzico.enabled=false")
                .withBean(IyzicoProperties.class, () -> new IyzicoProperties(false, "sandbox", null, null, null, null))
                .run(context -> assertThat(context).doesNotHaveBean(IyzicoClient.class));
    }

    @Test
    void whenIyzicoEnabled_andCredentialsPresent_activatesRealClient() {
        runner.withPropertyValues("payments.iyzico.enabled=true")
                .withBean(IyzicoProperties.class, () -> new IyzicoProperties(
                        true, "sandbox", "test-api-key", "test-secret-key", "https://sandbox-api.iyzipay.com", "http://localhost/webhook"
                ))
                .run(context -> {
                    assertThat(context).hasSingleBean(IyzicoClient.class);
                    assertThat(context.getBean(IyzicoClient.class)).isInstanceOf(RealIyzicoClient.class);
                });
    }

    @Test
    void whenIyzicoEnabled_andCredentialsMissing_failsFast() {
        runner.withPropertyValues("payments.iyzico.enabled=true")
                .withBean(IyzicoProperties.class, () -> new IyzicoProperties(
                        true, "sandbox", "", "test-secret-key", "https://sandbox-api.iyzipay.com", "http://localhost/webhook"
                ))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasStackTraceContaining("Iyzico API Key is required when iyzico is enabled");
                });
    }
}
