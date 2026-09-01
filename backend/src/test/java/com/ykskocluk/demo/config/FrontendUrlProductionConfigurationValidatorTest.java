package com.ykskocluk.demo.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.Environment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FrontendUrlProductionConfigurationValidatorTest {

    @Test
    void missingUrlFailsOutsideDevelopment() {
        assertThatThrownBy(() -> validator(null, "https://app.example.com/oauth/callback", profiles()).validate())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void localhostFailsOutsideDevelopment() {
        assertThatThrownBy(() -> validator("http://localhost:5173",
                "http://localhost:5173/oauth/callback", profiles()).validate())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void plainHttpFailsOutsideDevelopment() {
        assertThatThrownBy(() -> validator("http://app.example.com",
                "http://app.example.com/oauth/callback", profiles()).validate())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void malformedUrlFailsOutsideDevelopment() {
        assertThatThrownBy(() -> validator("not a url", "https://app.example.com/oauth/callback", profiles()).validate())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void userInfoFailsOutsideDevelopment() {
        assertThatThrownBy(() -> validator("https://user:pass@app.example.com",
                "https://app.example.com/oauth/callback", profiles()).validate())
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://127.0.0.1", "https://0.0.0.0", "https://[::1]",
            "https://10.0.0.5", "https://192.168.1.5"})
    void loopbackAndPrivateLiteralsFailOutsideDevelopment(String origin) {
        assertThatThrownBy(() -> validator(origin, origin + "/oauth/callback", profiles()).validate())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void explicitProductionUrlsPass() {
        assertThatCode(() -> validator("https://app.example.com",
                "https://app.example.com/oauth/callback", profiles()).validate()).doesNotThrowAnyException();
    }

    @Test
    void localTestAndStubProfilesAllowLocalhost() {
        for (String profile : new String[]{"local", "test", "stub"}) {
            assertThatCode(() -> validator("http://localhost:5173",
                    "http://localhost:5173/oauth/callback", profiles(profile)).validate())
                    .doesNotThrowAnyException();
        }
    }

    private FrontendUrlProductionConfigurationValidator validator(String frontendBase, String oauthRedirect,
                                                                  Environment environment) {
        return new FrontendUrlProductionConfigurationValidator(
                new PasswordSecurityProperties(frontendBase, Duration.ofMinutes(30), Duration.ofDays(7)),
                new MessageNotificationProperties(Duration.ofMinutes(30), frontendBase),
                oauthRedirect, environment);
    }

    private Environment profiles(String... activeProfiles) {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(activeProfiles);
        return environment;
    }
}
