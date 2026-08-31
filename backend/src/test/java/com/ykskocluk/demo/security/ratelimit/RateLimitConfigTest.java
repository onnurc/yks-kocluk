package com.ykskocluk.demo.security.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.env.MockEnvironment;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitConfigTest {

    private final RateLimitConfig config = new RateLimitConfig();
    private final InMemoryRateLimitStore inMemory = new InMemoryRateLimitStore(Clock.systemUTC());

    @Test
    void explicitLocalProfileUsesInMemoryStoreWithoutRedis() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        RateLimitStore store = config.rateLimitStore(
                inMemory, new RateLimitProperties(), environment, providerReturning(null));

        assertThat(store).isSameAs(inMemory);
    }

    @Test
    void productionRedisModeCreatesSharedAtomicStore() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);

        RateLimitStore store = config.rateLimitStore(
                inMemory, new RateLimitProperties(), new MockEnvironment(), providerReturning(redis));

        assertThat(store).isInstanceOf(RedisRateLimitStore.class);
    }

    @Test
    void productionRedisModeFailsStartupWhenRedisInfrastructureIsNotConfigured() {
        assertThatThrownBy(() -> config.rateLimitStore(
                inMemory, new RateLimitProperties(), new MockEnvironment(), providerReturning(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Redis rate limiting");
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<StringRedisTemplate> providerReturning(StringRedisTemplate template) {
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(template);
        return provider;
    }
}
