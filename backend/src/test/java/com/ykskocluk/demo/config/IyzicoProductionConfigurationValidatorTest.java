package com.ykskocluk.demo.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IyzicoProductionConfigurationValidatorTest {

    @Test
    void productionAcceptsApprovedProviderAndPublicHttpsCallback() {
        assertThatCode(() -> validator("https://api.iyzipay.com",
                "https://api.uniformakademi.com/api/v1/payments/iyzico/webhook", new MockEnvironment()).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void productionRejectsHttpLocalAndPrivateCallbacks() {
        for (String callback : new String[] {
                "http://api.uniformakademi.com/callback",
                "https://localhost/callback",
                "https://127.0.0.1/callback",
                "https://10.1.2.3/callback",
                "https://service.internal/callback"
        }) {
            assertThatThrownBy(() -> validator("https://api.iyzipay.com", callback, new MockEnvironment()).validate())
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void productionRejectsNonProviderOrInsecureBaseOrigin() {
        assertThatThrownBy(() -> validator("https://attacker.example", "https://api.uniformakademi.com/callback",
                new MockEnvironment()).validate()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> validator("http://api.iyzipay.com", "https://api.uniformakademi.com/callback",
                new MockEnvironment()).validate()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void explicitLocalProfileAllowsLocalPaymentConfiguration() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        assertThatCode(() -> validator("http://localhost:8080", "http://localhost:8080/callback", environment).validate())
                .doesNotThrowAnyException();
    }

    private IyzicoProductionConfigurationValidator validator(String baseUrl, String callbackUrl,
                                                              MockEnvironment environment) {
        return new IyzicoProductionConfigurationValidator(
                new IyzicoProperties(true, "production", "key", "secret", baseUrl, callbackUrl), environment);
    }
}
