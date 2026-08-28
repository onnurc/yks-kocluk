package com.ykskocluk.demo.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;

/**
 * Stub iyzico client — always returns success with a fake reference. No real money.
 */
@Component
@ConditionalOnProperty(name = "payments.iyzico.enabled", havingValue = "false", matchIfMissing = true)
@Profile({"local", "stub", "test"})
public class StubIyzicoClient implements IyzicoClient {

    private static final Logger log = LoggerFactory.getLogger(StubIyzicoClient.class);
    private final String frontendBaseUrl;

    public StubIyzicoClient(
            @Value("${payments.stub.frontend-base-url:${FRONTEND_BASE_URL:http://localhost:5173}}")
            String frontendBaseUrl) {
        URI base = URI.create(frontendBaseUrl);
        String host = base.getHost();
        boolean loopbackHost = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                || "::1".equals(host);
        if (!("http".equalsIgnoreCase(base.getScheme()) || "https".equalsIgnoreCase(base.getScheme()))
                || !loopbackHost || base.getUserInfo() != null || base.getQuery() != null
                || base.getFragment() != null) {
            throw new IllegalArgumentException("Stub payment frontend URL must be a local loopback origin");
        }
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    @Override
    public CheckoutResult initializeCheckout(Long subscriptionId, Long paymentId, BigDecimal amount, String idempotencyKey) {
        String token = "stub-checkout-" + paymentId + "-" + UUID.randomUUID();
        String url = frontendBaseUrl + "/payment/stub/" + token;
        log.info("[STUB IyzicoClient] checkout initialized for payment={}", paymentId);
        return new CheckoutResult(token, url);
    }

    @Override
    public ChargeResult charge(String savedCardToken, BigDecimal amount, String idempotencyKey) {
        String reference = "stub-ref-" + UUID.randomUUID();
        log.info("[STUB IyzicoClient] recurring charge completed");
        return new ChargeResult(true, reference);
    }

    @Override
    public RefundResult refund(String providerReference, BigDecimal amount, String idempotencyKey) {
        String reference = "stub-refund-ref-" + UUID.randomUUID();
        log.info("[STUB IyzicoClient] refund completed");
        return new RefundResult(true, reference, null, null);
    }
}
