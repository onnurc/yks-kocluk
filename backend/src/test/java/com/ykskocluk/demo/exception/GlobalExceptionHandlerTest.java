package com.ykskocluk.demo.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void paymentProviderErrorsDoNotExposeInternalDiagnostics() {
        String internal = "jdbc:postgresql://internal-db:5432/app?password=secret";

        var response = new GlobalExceptionHandler().handlePaymentProvider(
                new PaymentProviderException(internal));

        assertThat(response.getStatus()).isEqualTo(502);
        assertThat(response.getDetail())
                .isEqualTo("Ödeme sağlayıcısıyla iletişim kurulamadı. Lütfen tekrar deneyin.")
                .doesNotContain("jdbc", "password", "internal-db", "secret");
        assertThat(response.getProperties()).containsEntry("errorCode", "PAYMENT_PROVIDER_ERROR");
    }

    @Test
    void unexpectedErrorsDoNotExposeStackTraceLikeMessages() {
        String internal = "org.postgresql.util.PSQLException: relation users does not exist";

        var response = new GlobalExceptionHandler().handleUnexpected(new RuntimeException(internal));

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getDetail()).isEqualTo("Beklenmeyen bir hata oluştu")
                .doesNotContain("postgresql", "users", "RuntimeException");
    }
}
