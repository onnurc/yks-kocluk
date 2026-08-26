package com.ykskocluk.demo.security.ratelimit;

import com.ykskocluk.demo.exception.ApiException;
import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends ApiException {
    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        this(retryAfterSeconds, "RATE_LIMIT_EXCEEDED", "Too many requests. Please try again later.");
    }

    public RateLimitExceededException(long retryAfterSeconds, String errorCode, String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, errorCode, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
