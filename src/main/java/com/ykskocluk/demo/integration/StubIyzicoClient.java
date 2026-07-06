package com.ykskocluk.demo.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Stub iyzico client — always returns success with a fake reference. No real money.
 *
 * <p><strong>Stage-1 wiring:</strong> a plain {@code @Component} (the ONLY {@link IyzicoClient}
 * bean, active in every profile) — there is no real impl yet, so a {@code @Profile("test")} gate
 * would leave local/prod with no bean and break boot. <strong>Stage 2</strong> adds
 * {@code RealIyzicoClient @Profile("!test")} and flips this to {@code @Profile("test")} — the same
 * final shape as {@code StubMeetClient}/{@code StubMailClient}.
 */
@Component
public class StubIyzicoClient implements IyzicoClient {

    private static final Logger log = LoggerFactory.getLogger(StubIyzicoClient.class);

    @Override
    public CheckoutResult initializeCheckout(Long subscriptionId, Long paymentId, BigDecimal amount, String idempotencyKey) {
        String token = "stub-checkout-" + paymentId + "-" + UUID.randomUUID();
        String url = "https://checkout.stub.local/pay/" + token;
        log.info("[STUB IyzicoClient] checkout init sub={} payment={} amount={} key={} -> token {}",
                subscriptionId, paymentId, amount, idempotencyKey, token);
        return new CheckoutResult(token, url);
    }

    @Override
    public ChargeResult charge(String savedCardToken, BigDecimal amount, String idempotencyKey) {
        String reference = "stub-ref-" + UUID.randomUUID();
        log.info("[STUB IyzicoClient] charged {} (key {}) -> success, ref {}",
                amount, idempotencyKey, reference);
        return new ChargeResult(true, reference);
    }
}
