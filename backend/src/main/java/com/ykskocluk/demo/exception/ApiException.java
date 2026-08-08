package com.ykskocluk.demo.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import java.util.Map;

/**
 * Base application exception carrying an HTTP status and a machine-readable
 * {@code errorCode}. Rendered as RFC 9457 ProblemDetail by {@link GlobalExceptionHandler}.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;
    private final Map<String, Object> properties;

    public ApiException(HttpStatus status, String errorCode, String detail) {
        super(detail);
        this.status = status;
        this.errorCode = errorCode;
        this.properties = Map.of();
    }

    public ApiException(HttpStatus status, String errorCode, String detail, Map<String, Object> properties) {
        super(detail);
        this.status = status;
        this.errorCode = errorCode;
        this.properties = Map.copyOf(properties);
    }
}
