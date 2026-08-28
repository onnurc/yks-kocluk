package com.ykskocluk.demo.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Single source of truth for error responses. Every error is rendered as an
 * RFC 9457 {@link ProblemDetail} ({@code application/problem+json}) with the
 * project's custom {@code errorCode} + {@code timestamp} fields.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} and enriches the body of
 * <em>framework-thrown</em> exceptions too (404, 405, unreadable body, …) via
 * {@link #handleExceptionInternal}, so Spring's bare default ProblemDetail can
 * never leak alongside ours.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(com.ykskocluk.demo.security.ratelimit.RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimitExceededException(com.ykskocluk.demo.security.ratelimit.RateLimitExceededException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        pd.setProperty("errorCode", ex.getErrorCode());
        pd.setProperty("timestamp", Instant.now());

        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", String.valueOf(ex.getRetryAfterSeconds()));

        return new ResponseEntity<>(pd, headers, ex.getStatus());
    }

    /** Our own thrown exceptions. */
    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        pd.setProperty("errorCode", ex.getErrorCode());
        pd.setProperty("timestamp", Instant.now());
        ex.getProperties().forEach(pd::setProperty);
        return pd;
    }

    /**
     * Spring Security throws AccessDeniedException (incl. method-security AuthorizationDeniedException)
     * for authenticated-but-forbidden requests. Rethrow so the ExceptionTranslationFilter routes it to
     * our 403 ProblemDetail AccessDeniedHandler — otherwise the catch-all below would turn it into a 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDenied(AccessDeniedException ex) throws AccessDeniedException {
        throw ex;
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLocking(org.springframework.dao.OptimisticLockingFailureException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "İşlem çakışması tespit edildi. Lütfen tekrar deneyin.");
        pd.setProperty("errorCode", "CONCURRENT_UPDATE");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(PaymentProviderException.class)
    public ProblemDetail handlePaymentProvider(PaymentProviderException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY, "Ödeme sağlayıcısıyla iletişim kurulamadı. Lütfen tekrar deneyin.");
        pd.setProperty("errorCode", "PAYMENT_PROVIDER_ERROR");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /** Catch-all so unexpected exceptions still return our standard format, not a stack trace. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Beklenmeyen bir hata oluştu");
        pd.setProperty("errorCode", "INTERNAL_ERROR");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /** Bean Validation failures on @Valid request bodies → 400 + field-level error list. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Doğrulama hatası");
        pd.setProperty("errorCode", "VALIDATION_ERROR");
        pd.setProperty("timestamp", Instant.now());
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "message", fe.getDefaultMessage() == null ? "" : fe.getDefaultMessage()))
                .toList();
        pd.setProperty("errors", errors);
        return handleExceptionInternal(ex, pd, headers, status, request);
    }

    /**
     * Hook through which all {@link ResponseEntityExceptionHandler} handlers emit their
     * response body. We stamp {@code errorCode} + {@code timestamp} onto any ProblemDetail
     * that doesn't already carry them, covering framework-thrown 404/405/etc.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail pd) {
            Map<String, Object> props = pd.getProperties();
            if (props == null || !props.containsKey("errorCode")) {
                pd.setProperty("errorCode", defaultErrorCode(statusCode));
            }
            if (props == null || !props.containsKey("timestamp")) {
                pd.setProperty("timestamp", Instant.now());
            }
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private String defaultErrorCode(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "BAD_REQUEST";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> "ERROR_" + status.value();
        };
    }
}
