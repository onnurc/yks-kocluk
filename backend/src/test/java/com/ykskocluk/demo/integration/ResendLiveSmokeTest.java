package com.ykskocluk.demo.integration;

import com.ykskocluk.demo.config.ResendProperties;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * LIVE smoke against the real Resend API — sends ONE real booking-confirmation email through the
 * real {@link ResendMailClient} (real HTTP POST to Resend, real delivery attempt). {@code @Disabled}
 * by default so it can NEVER run in CI or a normal {@code ./mvnw verify} — JUnit skips
 * {@code @Disabled} tests even under a {@code -Dtest} filter, so there's no accidental Resend
 * send/quota burn from an automated run.
 *
 * <p>To run it manually, temporarily delete the {@code @Disabled} line (it is the
 * belt-and-suspenders guard for the build), then:
 *
 * <pre>
 *   RESEND_API_KEY=re_xxx RESEND_SMOKE_TO=you@example.com \
 *     ./mvnw test -Dtest=ResendLiveSmokeTest -DfailIfNoTests=false
 * </pre>
 *
 * Required env vars:
 * <ul>
 *   <li>{@code RESEND_API_KEY} — Resend secret key.</li>
 *   <li>{@code RESEND_SMOKE_TO} — recipient address for the test send.</li>
 * </ul>
 * Optional:
 * <ul>
 *   <li>{@code RESEND_FROM} — mirrors {@code app.resend.from}'s own env var, same default
 *   ({@code onboarding@resend.dev}) as {@code application.yml}. The point of this smoke test is to
 *   verify mail actually arrives through the real configuration (domain + SPF/DKIM + the real
 *   sender address), so set this to the same value production uses, e.g.
 *   {@code RESEND_FROM="Uniform Akademi <noreply@uniformakademi.com>"} — sending from the sandbox
 *   address would skip exactly what needs verifying. Left unset, sandbox restricts delivery to the
 *   Resend account owner's own email regardless of {@code RESEND_SMOKE_TO}; a verified-domain
 *   sender has no such restriction.</li>
 * </ul>
 * Success = a logged Resend message id + the email arriving in the {@code RESEND_SMOKE_TO} inbox.
 */
// @Disabled("Live Resend send — run manually with RESEND_API_KEY + RESEND_SMOKE_TO set (see class doc)")
class ResendLiveSmokeTest {

    @Test
    void sendsRealBookingConfirmation() {
        String apiKey = System.getenv("RESEND_API_KEY");
        String to = System.getenv("RESEND_SMOKE_TO");
        assumeTrue(apiKey != null && !apiKey.isBlank(), "RESEND_API_KEY not set");
        assumeTrue(to != null && !to.isBlank(), "RESEND_SMOKE_TO not set");

        // Mirrors app.resend.from's own RESEND_FROM env var + application.yml default, rather than
        // hardcoding either the sandbox or the real domain — see class doc.
        String from = System.getenv().getOrDefault("RESEND_FROM", "onboarding@resend.dev");

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        RestClient.Builder builder = RestClient.builder().requestFactory(factory);

        ResendMailClient client = new ResendMailClient(
                builder, new ResendProperties(apiKey, from, null));

        // Exercises the real client end-to-end (HTML build + POST). Errors are swallowed-and-logged
        // by the client, so a bad key surfaces as an error log, not an exception — check the log/inbox.
        client.sendSessionBooked(to, "Smoke Test Koç",
                Instant.now().plus(Duration.ofDays(1)), "https://meet.jit.si/yks-smoke-test");
    }
}
