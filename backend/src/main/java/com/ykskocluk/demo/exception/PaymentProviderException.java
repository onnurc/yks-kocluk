package com.ykskocluk.demo.exception;

/** Internal payment-provider failure whose details must never be returned to API clients. */
public class PaymentProviderException extends RuntimeException {

    public PaymentProviderException(String message) {
        super(message);
    }

    public PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
