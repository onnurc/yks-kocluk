package com.ykskocluk.demo.security.ratelimit;

public record RateLimitResult(
        boolean allowed,
        int remaining,
        long retryAfterSeconds
) {
}
