package com.ykskocluk.demo.security.ratelimit;

import java.time.Duration;

public interface RateLimitStore {
    RateLimitResult consume(String key, int limit, Duration window);
    default void cleanup(Duration maxAge) {}
}
