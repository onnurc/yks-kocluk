package com.ykskocluk.demo.integration;

import com.ykskocluk.demo.config.ResendProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * Unit test for {@link ResendMailClient} with the HTTP layer mocked ({@link MockRestServiceServer}
 * bound to the RestClient builder) — no real network, no Spring context. Proves the Resend request
 * shape on success, and the swallow-and-log totality on failure.
 */
class ResendMailClientTest {

    private static final String TO = "student@example.com";
    private static final String LINK = "https://meet.jit.si/yks-1234";

    private ResendMailClient clientBoundTo(MockRestServiceServer[] serverOut) {
        RestClient.Builder builder = RestClient.builder();
        serverOut[0] = MockRestServiceServer.bindTo(builder).build();
        return new ResendMailClient(builder, new ResendProperties("re_test_key", "onboarding@resend.dev"));
    }

    @Test
    void purchaseConfirmed_containsPlanCoachAmountButNoPaymentSecrets() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        ResendMailClient client = clientBoundTo(server);
        server[0].expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.to[0]").value(TO))
                .andExpect(jsonPath("$.subject").value("Uniform Akademi plan satın alımınız tamamlandı"))
                .andExpect(jsonPath("$.html", containsString("Aylık Koçluk")))
                .andExpect(jsonPath("$.html", containsString("Ayşe Koç")))
                .andExpect(jsonPath("$.html", containsString("1500.00")))
                .andExpect(jsonPath("$.html", org.hamcrest.Matchers.not(containsString("card"))))
                .andExpect(jsonPath("$.html", org.hamcrest.Matchers.not(containsString("token"))))
                .andRespond(withSuccess("{\"id\":\"msg_purchase\"}", APPLICATION_JSON));
        client.sendPurchaseConfirmed(TO, "Ali", "Aylık Koçluk", "Ayşe Koç",
                new BigDecimal("1500.00"), "TRY", Instant.parse("2026-08-01T10:00:00Z"),
                Instant.parse("2026-08-31T10:00:00Z"));
        server[0].verify();
    }

    @Test
    void send_postsToResend_withAuthHeaderAndExpectedBody() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        ResendMailClient client = clientBoundTo(server);

        server[0].expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer re_test_key"))
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.from").value("onboarding@resend.dev"))
                .andExpect(jsonPath("$.to[0]").value(TO))
                .andExpect(jsonPath("$.subject").value("Görüşmeniz planlandı"))
                .andExpect(jsonPath("$.html", containsString(LINK)))
                .andRespond(withSuccess("{\"id\":\"msg_abc123\"}", APPLICATION_JSON));

        client.sendSessionBooked(TO, "Ayşe Koç", Instant.parse("2026-06-22T18:00:00Z"), LINK);

        server[0].verify();
    }

    @Test
    void send_resendReturns500_isSwallowed_neverThrows() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        ResendMailClient client = clientBoundTo(server);

        server[0].expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withServerError());

        // Best-effort: a Resend failure must never propagate (the booking is already committed).
        assertThatCode(() ->
                client.sendSessionBooked(TO, "Ayşe Koç", Instant.parse("2026-06-22T18:00:00Z"), LINK))
                .doesNotThrowAnyException();

        server[0].verify();
    }

    @Test
    void renewalSucceeded_postsExpectedSubjectAndBody() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        ResendMailClient client = clientBoundTo(server);

        server[0].expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.to[0]").value(TO))
                .andExpect(jsonPath("$.subject").value("Aboneliğiniz yenilendi"))
                .andExpect(jsonPath("$.html", containsString("1500")))
                .andRespond(withSuccess("{\"id\":\"msg_x\"}", APPLICATION_JSON));

        client.sendRenewalSucceeded(TO, "Ayşe Koç", Instant.parse("2026-07-20T00:00:00Z"), new BigDecimal("1500.00"));

        server[0].verify();
    }

    @Test
    void paymentFailed_resendReturns500_isSwallowed() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        ResendMailClient client = clientBoundTo(server);

        server[0].expect(requestTo("https://api.resend.com/emails")).andRespond(withServerError());

        // Every new method is best-effort too — a 500 must never propagate.
        assertThatCode(() -> client.sendPaymentFailed(TO, "Ayşe Koç", 1, 3)).doesNotThrowAnyException();

        server[0].verify();
    }

    @Test
    void passwordReset_containsSingleUseLinkAndExpiryButNoPassword() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        ResendMailClient client = clientBoundTo(server);
        String resetLink = "https://app.example.com/reset-password?token=secret-token";
        server[0].expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.subject").value("Uniform Akademi şifre sıfırlama bağlantısı"))
                .andExpect(jsonPath("$.html", containsString(resetLink)))
                .andExpect(jsonPath("$.html", containsString("30 dakika")))
                .andExpect(jsonPath("$.html", containsString("tek kullanımlık")))
                .andRespond(withSuccess("{\"id\":\"msg_reset\"}", APPLICATION_JSON));
        client.sendPasswordReset(TO, resetLink);
        server[0].verify();
    }

    @Test
    void emailVerificationContainsCodeAndTenMinuteExpiryButNoPassword() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        ResendMailClient client = clientBoundTo(server);
        server[0].expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.subject").value("Uniform Akademi e-posta doğrulama kodu"))
                .andExpect(jsonPath("$.html", containsString("123456")))
                .andExpect(jsonPath("$.html", containsString("10 dakika")))
                .andExpect(jsonPath("$.html", org.hamcrest.Matchers.not(containsString("şifre"))))
                .andRespond(withSuccess("{\"id\":\"msg_verify\"}", APPLICATION_JSON));
        client.sendEmailVerification(TO, "123456");
        server[0].verify();
    }
}
