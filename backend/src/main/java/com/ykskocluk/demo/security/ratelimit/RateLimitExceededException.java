package com.ykskocluk.demo.security.ratelimit;

import com.ykskocluk.demo.exception.ApiException;
import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends ApiException {
    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", "Too many requests. Please try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
