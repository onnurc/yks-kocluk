package com.ykskocluk.demo.security.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public InMemoryRateLimitStore inMemoryRateLimitStore(Clock clock) {
        return new InMemoryRateLimitStore(clock);
    }

    @Bean
    @Primary
    public RateLimitStore rateLimitStore(InMemoryRateLimitStore inMemory,
                                         RateLimitProperties properties,
                                         Environment environment,
                                         ObjectProvider<StringRedisTemplate> redisTemplate) {
        if (environment.acceptsProfiles(Profiles.of("local", "test", "stub"))
                || properties.getStore() == RateLimitProperties.Store.IN_MEMORY) {
            return inMemory;
        }
        StringRedisTemplate template = redisTemplate.getIfAvailable();
        if (template == null) {
            throw new IllegalStateException("Redis rate limiting requires Spring Redis configuration");
        }
        return new RedisRateLimitStore(template, inMemory, properties);
    }
}
