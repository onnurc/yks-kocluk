package com.ykskocluk.demo.integration;

import com.ykskocluk.demo.config.ResendProperties;
import com.ykskocluk.demo.enums.ReportStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
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
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final Locale TR = Locale.of("tr", "TR");
    // Store UTC, display Europe/Istanbul (CLAUDE.md). e.g. "22 Haziran 2026, 21:00".
    private static final DateTimeFormatter WHEN_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", TR);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy", TR);

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
        String when = WHEN_FORMAT.format(startTime.atZone(ISTANBUL));
        send(toEmail, "Görüşmeniz planlandı", """
                <div style="font-family:sans-serif;line-height:1.5">
                  <h2>Görüşmeniz planlandı</h2>
                  <p>Koçunuz <strong>%s</strong> ile görüşmeniz <strong>%s</strong> tarihinde planlandı.</p>
                  <p><a href="%s">Görüşmeye katıl</a></p>
                </div>
                """.formatted(coachName, when, meetLink));
    }

    @Override
    public void sendRenewalSucceeded(String toEmail, String coachName, Instant nextEndAt, BigDecimal amount) {
        String next = DATE_FORMAT.format(nextEndAt.atZone(ISTANBUL));
        send(toEmail, "Aboneliğiniz yenilendi", """
                <div style="font-family:sans-serif;line-height:1.5">
                  <h2>Aboneliğiniz yenilendi</h2>
                  <p>Koçunuz <strong>%s</strong> ile aboneliğiniz yenilendi. Tahsil edilen tutar:
                     <strong>%s TL</strong>. Sonraki yenileme tarihi: <strong>%s</strong>.</p>
                </div>
                """.formatted(coachName, amount.toPlainString(), next));
    }

    @Override
    public void sendPaymentFailed(String toEmail, String coachName, int attemptNumber, int maxAttempts) {
        send(toEmail, "Ödeme alınamadı", """
                <div style="font-family:sans-serif;line-height:1.5">
                  <h2>Ödeme alınamadı</h2>
                  <p>Koçunuz <strong>%s</strong> ile aboneliğinizin yenileme ödemesi alınamadı
                     (deneme %d/%d). Lütfen kart bilgilerinizi kontrol edin — erişiminiz şimdilik açık.</p>
                </div>
                """.formatted(coachName, attemptNumber, maxAttempts));
    }

    @Override
    public void sendSubscriptionExpired(String toEmail, String coachName) {
        send(toEmail, "Aboneliğiniz sona erdi", """
                <div style="font-family:sans-serif;line-height:1.5">
                  <h2>Aboneliğiniz sona erdi</h2>
                  <p>Koçunuz <strong>%s</strong> ile aboneliğiniz sona erdi ve erişiminiz kapandı.
                     Devam etmek için yeniden abone olabilirsiniz.</p>
                </div>
                """.formatted(coachName));
    }

    @Override
    public void sendCancellationConfirmed(String toEmail, String coachName, Instant accessUntil) {
        String until = DATE_FORMAT.format(accessUntil.atZone(ISTANBUL));
        send(toEmail, "Abonelik iptaliniz alındı", """
                <div style="font-family:sans-serif;line-height:1.5">
                  <h2>Abonelik iptaliniz alındı</h2>
                  <p>Koçunuz <strong>%s</strong> ile aboneliğiniz yenilenmeyecek. Erişiminiz
                     <strong>%s</strong> tarihine kadar açık kalacaktır.</p>
                </div>
                """.formatted(coachName, until));
    }

    @Override
    public void sendReportReceived(String toEmail) {
        send(toEmail, "Şikayetiniz alındı", """
                <div style="font-family:sans-serif;line-height:1.5">
                  <h2>Şikayetiniz alındı</h2>
                  <p>Şikayetiniz başarıyla alınmıştır. Moderasyon ekibimiz platform kuralları
                     doğrultusunda inceleme yapacaktır.</p>
                </div>
                """);
    }

    @Override
    public void sendReportStatusUpdated(String toEmail, ReportStatus newStatus) {
        String subject = switch (newStatus) {
            case REVIEWED -> "Şikayetiniz incelemeye alındı";
            case RESOLVED -> "Şikayetiniz sonuçlandırıldı";
            case DISMISSED -> "Şikayetiniz kapatıldı";
            default -> "Şikayet durumu güncellendi";
        };
        String body = switch (newStatus) {
            case REVIEWED -> "Şikayetiniz moderasyon ekibimiz tarafından incelemeye alınmıştır.";
            case RESOLVED -> "Şikayetiniz değerlendirilmiş ve sonuçlandırılmıştır. Gerekli işlemler platform kuralları doğrultusunda uygulanmıştır.";
            case DISMISSED -> "Şikayetiniz değerlendirilmiş ve kapatılmıştır.";
            default -> "Şikayetinizin durumu güncellenmiştir.";
        };
        send(toEmail, subject, """
                <div style="font-family:sans-serif;line-height:1.5">
                  <h2>%s</h2>
                  <p>%s</p>
                </div>
                """.formatted(subject, body));
    }

    @Override
    public void sendUserSuspended(String toEmail) {
        send(toEmail, "Hesabınız askıya alındı", """
                <div style="font-family:sans-serif;line-height:1.5">
                  <h2>Hesabınız askıya alındı</h2>
                  <p>Hesabınıza erişim platform kuralları doğrultusunda kısıtlanmıştır.
                     Detaylı bilgi için destek ekibimizle iletişime geçebilirsiniz.</p>
                </div>
                """);
    }

    @Override
    public void sendPasswordReset(String toEmail, String resetLink) {
        send(toEmail, "Uniform Akademi şifre sıfırlama bağlantısı", """
                <div style="font-family:sans-serif;line-height:1.5">
                  <h2>Şifrenizi sıfırlayın</h2><p>Merhaba,</p>
                  <p><a href="%s">Yeni şifrenizi belirleyin</a></p>
                  <p>Bu tek kullanımlık bağlantı 30 dakika sonra geçersiz olur.</p>
                  <p>Bu isteği siz yapmadıysanız bu e-postayı yok sayabilirsiniz.</p>
                </div>
                """.formatted(resetLink));
    }

    @Override
    public void sendEmailVerification(String toEmail, String code) {
        send(toEmail, "Uniform Akademi e-posta doğrulama kodu", """
                <div style="font-family:sans-serif;line-height:1.5">
                  <h2>E-posta adresinizi doğrulayın</h2>
                  <p>Uniform Akademi hesabınızın e-posta adresini doğrulamak için aşağıdaki kodu kullanın:</p>
                  <p style="font-size:28px;font-weight:700;letter-spacing:6px">%s</p>
                  <p>Bu kod 10 dakika geçerlidir ve yalnızca bir kez kullanılabilir.</p>
                  <p>Bu kaydı siz yapmadıysanız bu e-postayı yok sayabilirsiniz.</p>
                </div>
                """.formatted(code));
    }

    /** Single best-effort POST to Resend — swallows-and-logs every error (timeouts included). */
    private void send(String toEmail, String subject, String html) {
        try {
            var request = new ResendEmailRequest(properties.from(), List.of(toEmail), subject, html);
            ResendEmailResponse response = restClient.post()
                    .uri("/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ResendEmailResponse.class);
            log.info("[Resend] '{}' sent to {} (id {})", subject, toEmail,
                    response != null ? response.id() : "<none>");
        } catch (Exception e) {
            // Best-effort: the originating DB tx is already committed. Log and move on, never throw.
            log.error("[Resend] failed to send '{}' to {}: {}", subject, toEmail, e.getMessage(), e);
        }
    }

    /** Resend send-email request body. */
    private record ResendEmailRequest(String from, List<String> to, String subject, String html) {
    }

    /** Resend send-email response — we only need the message id. */
    private record ResendEmailResponse(String id) {
    }
}
