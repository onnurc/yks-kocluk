package com.ykskocluk.demo.security.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;

/** Atomic fixed-window counter shared by every backend replica. */
public class RedisRateLimitStore implements RateLimitStore {
    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitStore.class);
    private static final DefaultRedisScript<String> CONSUME = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            local ttl = redis.call('PTTL', KEYS[1])
            return tostring(count) .. ':' .. tostring(ttl)
            """, String.class);

    private final StringRedisTemplate redis;
    private final InMemoryRateLimitStore fallback;
    private final RateLimitProperties properties;

    public RedisRateLimitStore(StringRedisTemplate redis, InMemoryRateLimitStore fallback,
                               RateLimitProperties properties) {
        this.redis = redis;
        this.fallback = fallback;
        this.properties = properties;
    }

    @Override
    public RateLimitResult consume(String key, int limit, Duration window) {
        try {
            String result = redis.execute(CONSUME, List.of(key), Long.toString(window.toMillis()));
            if (result == null) throw new IllegalStateException("Redis returned no rate-limit result");
            String[] values = result.split(":", 2);
            long count = Long.parseLong(values[0]);
            long ttlMillis = Math.max(0, Long.parseLong(values[1]));
            boolean allowed = count <= limit;
            int remaining = Math.max(0, limit - Math.toIntExact(Math.min(count, Integer.MAX_VALUE)));
            long retryAfter = allowed ? 0 : Math.max(1, (ttlMillis + 999) / 1000);
            return new RateLimitResult(allowed, remaining, retryAfter);
        } catch (RuntimeException failure) {
            if (properties.getFailurePolicy() == RateLimitProperties.FailurePolicy.IN_MEMORY_FALLBACK) {
                log.warn("Shared rate-limit store unavailable; applying explicit in-memory fallback policy");
                return fallback.consume(key, limit, window);
            }
            throw new RateLimitStoreUnavailableException(failure);
        }
    }
}
