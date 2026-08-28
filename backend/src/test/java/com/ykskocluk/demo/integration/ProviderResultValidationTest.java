package com.ykskocluk.demo.integration;

import com.ykskocluk.demo.exception.PaymentProviderException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderResultValidationTest {

    @Test
    void successfulChargeAndRefundRequireProviderReferences() {
        assertThatThrownBy(() -> new ChargeResult(true, " "))
                .isInstanceOf(PaymentProviderException.class);
        assertThatThrownBy(() -> new RefundResult(true, null, null, null))
                .isInstanceOf(PaymentProviderException.class);
    }

    @Test
    void failedProviderResultsMayOmitReferences() {
        assertThatCode(() -> new ChargeResult(false, null)).doesNotThrowAnyException();
        assertThatCode(() -> new RefundResult(false, null, "DECLINED", null)).doesNotThrowAnyException();
    }
}
