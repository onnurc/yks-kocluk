package com.ykskocluk.demo.integration;

import com.ykskocluk.demo.config.ResendProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Real {@link MailClient} (Phase 7) — transactional email via Resend (POST https://api.resend.com/emails).
 * Active under every non-{@code test} profile; {@link StubMailClient} stays active under {@code test}
 * (mutually exclusive profiles → exactly one bean).
 *
 * <p><strong>Best-effort, total method.</strong> The after-commit listener calls this AFTER the
 * booking has committed, so a failure cannot roll back anything. On top of the listener's own
 * try/catch, {@code sendSessionBooked} swallows-and-logs every error (timeouts included) and never
 * throws — a Resend outage/hang can never surface to the seam. Timeouts live on the injected
 * builder (see {@link ResendProperties} / {@code ResendConfig}).
 */
@Component
@Profile("!test")
public class ResendMailClient implements MailClient {

    private static final Logger log = LoggerFactory.getLogger(ResendMailClient.class);

    private static final String BASE_URL = "https://api.resend.com";
    private static final String SUBJECT = "Görüşmeniz planlandı";
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    // Store UTC, display Europe/Istanbul (CLAUDE.md). e.g. "22 Haziran 2026, 21:00".
    private static final DateTimeFormatter WHEN_FORMAT =
            DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.of("tr", "TR"));

    private final RestClient restClient;
    private final ResendProperties properties;

    public ResendMailClient(RestClient.Builder resendRestClientBuilder, ResendProperties properties) {
        this.properties = properties;
        this.restClient = resendRestClientBuilder
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .build();
    }

    @Override
    public void sendSessionBooked(String toEmail, String coachName, Instant startTime, String meetLink) {
        try {
            var request = new ResendEmailRequest(
                    properties.from(), List.of(toEmail), SUBJECT, buildHtml(coachName, startTime, meetLink));
            ResendEmailResponse response = restClient.post()
                    .uri("/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ResendEmailResponse.class);
            log.info("[Resend] booking confirmation sent to {} (id {})",
                    toEmail, response != null ? response.id() : "<none>");
        } catch (Exception e) {
            // Best-effort: the booking is already committed. Log the Resend failure and move on.
            log.error("[Resend] failed to send booking confirmation to {}: {}", toEmail, e.getMessage(), e);
        }
    }

    private String buildHtml(String coachName, Instant startTime, String meetLink) {
        String when = WHEN_FORMAT.format(startTime.atZone(ISTANBUL));
        return """
                <div style="font-family:sans-serif;line-height:1.5">
                  <h2>Görüşmeniz planlandı</h2>
                  <p>Koçunuz <strong>%s</strong> ile görüşmeniz <strong>%s</strong> tarihinde planlandı.</p>
                  <p><a href="%s">Görüşmeye katıl</a></p>
                </div>
                """.formatted(coachName, when, meetLink);
    }

    /** Resend send-email request body. */
    private record ResendEmailRequest(String from, List<String> to, String subject, String html) {
    }

    /** Resend send-email response — we only need the message id. */
    private record ResendEmailResponse(String id) {
    }
}
