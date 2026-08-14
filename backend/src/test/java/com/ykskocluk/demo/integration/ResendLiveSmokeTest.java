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
 * LIVE smoke against the real Resend API — sends ONE booking-confirmation email through the real
 * {@link ResendMailClient} (real HTTP, real send). {@code @Disabled} by default so it can NEVER run
 * in CI or a normal {@code ./mvnw verify}. To run it manually (Phase 7 follow-up step):
 *
 * <pre>
 *   RESEND_API_KEY=re_xxx RESEND_SMOKE_TO=you@example.com \
 *     ./mvnw test -Dtest=ResendLiveSmokeTest -DfailIfNoTests=false
 * </pre>
 *
 * and temporarily remove {@code @Disabled} (it is the belt-and-suspenders guard for the build).
 * Sender is Resend's sandbox onboarding address, which sends without domain verification — the
 * recipient must be the Resend account owner's email. Success = a logged Resend message id + the
 * email arriving in that inbox.
 */
@Disabled("Live Resend send — run manually with RESEND_API_KEY + RESEND_SMOKE_TO set (see class doc)")
class ResendLiveSmokeTest {

    @Test
    void sendsRealBookingConfirmation() {
        String apiKey = System.getenv("RESEND_API_KEY");
        String to = System.getenv("RESEND_SMOKE_TO");
        assumeTrue(apiKey != null && !apiKey.isBlank(), "RESEND_API_KEY not set");
        assumeTrue(to != null && !to.isBlank(), "RESEND_SMOKE_TO not set");

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        RestClient.Builder builder = RestClient.builder().requestFactory(factory);

        ResendMailClient client = new ResendMailClient(
                builder, new ResendProperties(apiKey, "onboarding@resend.dev", null));

        // Exercises the real client end-to-end (HTML build + POST). Errors are swallowed-and-logged
        // by the client, so a bad key surfaces as an error log, not an exception — check the log/inbox.
        client.sendSessionBooked(to, "Smoke Test Koç",
                Instant.now().plus(Duration.ofDays(1)), "https://meet.jit.si/yks-smoke-test");
    }
}
