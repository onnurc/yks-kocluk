package com.ykskocluk.demo.security.ratelimit;

public class RateLimitStoreUnavailableException extends RuntimeException {
    public RateLimitStoreUnavailableException(Throwable cause) {
        super("Rate-limit store is temporarily unavailable", cause);
    }
}
