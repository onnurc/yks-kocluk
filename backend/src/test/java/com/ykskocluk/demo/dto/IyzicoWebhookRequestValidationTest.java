package com.ykskocluk.demo.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IyzicoWebhookRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsBlankOrUnreasonablyLongProviderFields() {
        IyzicoWebhookRequest request = new IyzicoWebhookRequest(
                1L, " ", "r".repeat(513), "e".repeat(65), "c".repeat(129));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("status", "providerReference", "iyziEventType", "paymentConversationId");
    }

    @Test
    void acceptsBoundedSignedWebhookFields() {
        IyzicoWebhookRequest request = new IyzicoWebhookRequest(
                1L, "SUCCESS", "provider-reference", "PAYMENT_API", "1");

        assertThat(validator.validate(request)).isEmpty();
    }
}
