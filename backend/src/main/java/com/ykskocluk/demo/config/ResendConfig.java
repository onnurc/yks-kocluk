package com.ykskocluk.demo.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Resend transport wiring (Phase 7). {@code @Profile("!test")} so the test profile never
 * loads it — under {@code test} the {@code StubMailClient} is active and zero network is wired.
 *
 * <p>The {@link RestClient.Builder} carries the timeouts that keep a hung Resend call from
 * blocking the after-commit notification thread forever (connect 3s / read 5s). Declaring our
 * own builder makes Boot's auto-configured one back off ({@code @ConditionalOnMissingBean}),
 * so {@code ResendMailClient} injects exactly this one.
 */
@Configuration
@Profile("!test")
@EnableConfigurationProperties(ResendProperties.class)
public class ResendConfig {

    @Bean
    RestClient.Builder resendRestClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);  // 3s — don't let a hung Resend connect block the thread
        factory.setReadTimeout(5000);     // 5s
        return RestClient.builder().requestFactory(factory);
    }

    /**
     * Fail-fast guard, scoped to {@code !local & !stub} (this project has no dedicated {@code prod}
     * Spring profile — Railway runs the bare/default profile, which is exactly what this excludes
     * local and stub from). Outside local/stub, {@code RESEND_FROM} must be explicitly supplied:
     * {@code app.resend.from}'s {@code onboarding@resend.dev} default in application.yml is a Resend
     * sandbox sender meant for dev convenience, not for real user-facing mail. Checks
     * {@code Environment.containsProperty} rather than {@link ResendProperties#from()}, since the
     * latter is never blank (the yaml placeholder default always resolves to something).
     */
    @Bean
    @Profile("!local & !stub")
    InitializingBean requireExplicitResendFrom(Environment environment) {
        return () -> {
            if (!environment.containsProperty("RESEND_FROM")) {
                throw new IllegalStateException(
                        "RESEND_FROM must be set outside local/stub/test — refusing to silently "
                        + "send mail from the onboarding@resend.dev sandbox sender.");
            }
        };
    }
}
