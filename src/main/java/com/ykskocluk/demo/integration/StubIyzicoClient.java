package com.ykskocluk.demo.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Stub iyzico client — always returns success with a fake reference. No real money.
 */
@Component
@ConditionalOnProperty(name = "payments.iyzico.enabled", havingValue = "false", matchIfMissing = true)
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
