package com.ykskocluk.demo.integration;

import com.iyzipay.model.CheckoutFormInitialize;
import com.iyzipay.model.Status;
import com.ykskocluk.demo.config.IyzicoProperties;
import com.ykskocluk.demo.exception.PaymentProviderException;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

class RealIyzicoClientTest {

    private final IyzicoProperties properties = new IyzicoProperties(
            true, "sandbox", "test-api-key", "test-secret-key", "https://sandbox-api.iyzipay.com", "http://localhost/webhook"
    );

    @Test
    void initializeCheckout_success_returnsCheckoutResult() {
        RealIyzicoClient client = new RealIyzicoClient(properties);

        CheckoutFormInitialize mockResponse = Mockito.mock(CheckoutFormInitialize.class);
        Mockito.when(mockResponse.getStatus()).thenReturn(Status.SUCCESS.getValue());
        Mockito.when(mockResponse.getToken()).thenReturn("test-checkout-token");
        Mockito.when(mockResponse.getPaymentPageUrl()).thenReturn("https://sandbox-checkout.iyzico.com/pay");

        try (MockedStatic<CheckoutFormInitialize> mockedStatic = Mockito.mockStatic(CheckoutFormInitialize.class)) {
            mockedStatic.when(() -> CheckoutFormInitialize.create(any(), any())).thenReturn(mockResponse);

            CheckoutResult result = client.initializeCheckout(1L, 2L, new BigDecimal("150.00"), "idemp-key");

            assertThat(result).isNotNull();
            assertThat(result.checkoutToken()).isEqualTo("test-checkout-token");
            assertThat(result.checkoutUrl()).isEqualTo("https://sandbox-checkout.iyzico.com/pay");
        }
    }

    @Test
    void initializeCheckout_failure_doesNotExposeProviderMessage() {
        RealIyzicoClient client = new RealIyzicoClient(properties);

        CheckoutFormInitialize mockResponse = Mockito.mock(CheckoutFormInitialize.class);
        Mockito.when(mockResponse.getStatus()).thenReturn(Status.FAILURE.getValue());
        Mockito.when(mockResponse.getErrorCode()).thenReturn("1001");
        Mockito.when(mockResponse.getErrorMessage()).thenReturn("Signature mismatch or generic error");

        try (MockedStatic<CheckoutFormInitialize> mockedStatic = Mockito.mockStatic(CheckoutFormInitialize.class)) {
            mockedStatic.when(() -> CheckoutFormInitialize.create(any(), any())).thenReturn(mockResponse);

            assertThatThrownBy(() -> client.initializeCheckout(1L, 2L, new BigDecimal("150.00"), "idemp-key"))
                    .isInstanceOf(PaymentProviderException.class)
                    .hasMessage("Iyzico rejected checkout initialization")
                    .hasMessageNotContaining("Signature mismatch");
        }
    }

    @Test
    void initializeCheckout_successWithoutRequiredProviderFields_isRejected() {
        RealIyzicoClient client = new RealIyzicoClient(properties);
        CheckoutFormInitialize mockResponse = Mockito.mock(CheckoutFormInitialize.class);
        Mockito.when(mockResponse.getStatus()).thenReturn(Status.SUCCESS.getValue());
        Mockito.when(mockResponse.getToken()).thenReturn(null);
        Mockito.when(mockResponse.getPaymentPageUrl()).thenReturn("https://sandbox-checkout.iyzico.com/pay");

        try (MockedStatic<CheckoutFormInitialize> mockedStatic = Mockito.mockStatic(CheckoutFormInitialize.class)) {
            mockedStatic.when(() -> CheckoutFormInitialize.create(any(), any())).thenReturn(mockResponse);
            assertThatThrownBy(() -> client.initializeCheckout(1L, 2L, new BigDecimal("150.00"), "idemp-key"))
                    .isInstanceOf(PaymentProviderException.class)
                    .hasMessage("Iyzico returned incomplete checkout data");
        }
    }

    @Test
    void recurringChargeFailsClosedInsteadOfReturningSyntheticSuccess() {
        RealIyzicoClient client = new RealIyzicoClient(properties);

        assertThatThrownBy(() -> client.charge("saved-card-token", new BigDecimal("150.00"), "idemp-key"))
                .isInstanceOf(PaymentProviderException.class)
                .hasMessage("Iyzico recurring charge is not implemented");
    }
}
