package com.ykskocluk.demo.integration;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link StubIyzicoClient} — no Spring, no I/O. The stub always succeeds (Stage 1)
 * and returns a non-blank provider reference so the billing flow can be built and tested before
 * real iyzico exists.
 */
class StubIyzicoClientTest {

    private final StubIyzicoClient client = new StubIyzicoClient("http://localhost:5173");

    @Test
    void checkout_pointsToLocalFrontendRoute_withoutExternalDns() {
        CheckoutResult result = client.initializeCheckout(10L, 25L, BigDecimal.TEN, "checkout:10");

        assertThat(result.checkoutUrl()).startsWith("http://localhost:5173/payment/stub/stub-checkout-25-");
        assertThat(result.checkoutUrl()).doesNotContain("checkout.stub.local");
    }

    @Test
    void charge_alwaysSucceeds_withReference() {
        ChargeResult result = client.charge("stub-card-token-x", new BigDecimal("1500.00"), "charge:1:2026-06-27");

        assertThat(result.success()).isTrue();
        assertThat(result.providerReference()).isNotBlank().startsWith("stub-ref-");
    }

    @Test
    void successiveCharges_returnDistinctReferences() {
        String a = client.charge("t", BigDecimal.TEN, "k1").providerReference();
        String b = client.charge("t", BigDecimal.TEN, "k2").providerReference();

        assertThat(a).isNotEqualTo(b);
    }
}
