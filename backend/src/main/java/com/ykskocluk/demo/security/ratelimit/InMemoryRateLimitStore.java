package com.ykskocluk.demo.security.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryRateLimitStore implements RateLimitStore {
    private final Clock clock;
    private final Map<String, WindowBucket> store = new ConcurrentHashMap<>();

    public InMemoryRateLimitStore(Clock clock) {
        this.clock = clock;
    }

    private static class WindowBucket {
        final long windowStartMs;
        final int count;

        WindowBucket(long windowStartMs, int count) {
            this.windowStartMs = windowStartMs;
            this.count = count;
        }
    }

    @Override
    public RateLimitResult consume(String key, int limit, Duration window) {
        long now = clock.millis();
        long windowMs = window.toMillis();

        WindowBucket updated = store.compute(key, (k, current) -> {
            if (current == null || (now - current.windowStartMs) >= windowMs) {
                // New window starts
                return new WindowBucket(now, 1);
            } else {
                // Increment count in current window
                return new WindowBucket(current.windowStartMs, current.count + 1);
            }
        });

        long elapsed = now - updated.windowStartMs;
        long timeRemainingMs = windowMs - elapsed;
        long retryAfterSeconds = Math.max(0, (timeRemainingMs + 999) / 1000); // ceiling division

        boolean allowed = updated.count <= limit;
        int remaining = Math.max(0, limit - updated.count);

        return new RateLimitResult(allowed, remaining, allowed ? 0 : retryAfterSeconds);
    }

    @Override
    public void cleanup(Duration maxAge) {
        long maxAgeMs = maxAge.toMillis();
        long now = clock.millis();
        store.entrySet().removeIf(entry -> (now - entry.getValue().windowStartMs) >= maxAgeMs);
    }

    /**
     * Drops every bucket. Not part of {@link RateLimitStore} — this is a full reset, not the
     * age-based sweep {@code cleanup} does, and the only caller is the test-side
     * {@code RateLimitResetTestExecutionListener} (this bean is a singleton shared by every
     * {@code @SpringBootTest} in the suite via Spring Test's context cache, so without a reset,
     * one test class's login/register calls count toward the next class's limit).
     */
    public void clear() {
        store.clear();
    }
}
