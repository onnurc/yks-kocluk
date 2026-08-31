package com.ykskocluk.demo.security.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisRateLimitStoreTest {
    @Test
    void sharedAtomicResultIsMappedToLimitDecision() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), any(), any(Object[].class))).thenReturn("4:4500");
        RateLimitProperties properties = new RateLimitProperties();
        RedisRateLimitStore store = new RedisRateLimitStore(redis,
                new InMemoryRateLimitStore(Clock.systemUTC()), properties);

        RateLimitResult result = store.consume("rate:test", 3, Duration.ofSeconds(60));

        assertThat(result.allowed()).isFalse();
        assertThat(result.remaining()).isZero();
        assertThat(result.retryAfterSeconds()).isEqualTo(5);
    }

    @Test
    void explicitFallbackPolicyKeepsProtectionActiveDuringRedisFailure() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), any(), any(Object[].class))).thenThrow(new IllegalStateException("offline"));
        RateLimitProperties properties = new RateLimitProperties();
        properties.setFailurePolicy(RateLimitProperties.FailurePolicy.IN_MEMORY_FALLBACK);
        RedisRateLimitStore store = new RedisRateLimitStore(redis,
                new InMemoryRateLimitStore(Clock.systemUTC()), properties);

        assertThat(store.consume("rate:test", 1, Duration.ofMinutes(1)).allowed()).isTrue();
        assertThat(store.consume("rate:test", 1, Duration.ofMinutes(1)).allowed()).isFalse();
    }

    @Test
    void failClosedPolicyReturnsControlledUnavailableError() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), any(), any(Object[].class))).thenThrow(new IllegalStateException("offline"));
        RateLimitProperties properties = new RateLimitProperties();
        properties.setFailurePolicy(RateLimitProperties.FailurePolicy.FAIL_CLOSED);
        RedisRateLimitStore store = new RedisRateLimitStore(redis,
                new InMemoryRateLimitStore(Clock.systemUTC()), properties);

        assertThatThrownBy(() -> store.consume("rate:test", 1, Duration.ofMinutes(1)))
                .isInstanceOf(RateLimitStoreUnavailableException.class);
    }
}
