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

    private final StubIyzicoClient client = new StubIyzicoClient();

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
