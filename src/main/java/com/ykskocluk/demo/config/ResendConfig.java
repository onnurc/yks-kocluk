package com.ykskocluk.demo.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
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
}
