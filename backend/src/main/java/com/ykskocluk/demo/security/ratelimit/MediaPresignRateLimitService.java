package com.ykskocluk.demo.security.ratelimit;

import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class MediaPresignRateLimitService {
    private final RateLimitStore store;
    private final RateLimitProperties properties;

    public MediaPresignRateLimitService(RateLimitStore store, RateLimitProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    public void check(Long userId) {
        if (!properties.isEnabled()) return;
        RateLimitProperties.UserLimitRule rule = properties.getMediaPresign();
        String key = "yks:%s:rate-limit:media:presign:user:%d"
                .formatted(properties.getEnvironment(), userId);
        RateLimitResult result = store.consume(key, rule.getUserLimit(), Duration.ofSeconds(rule.getWindowSeconds()));
        if (!result.allowed()) {
            throw new RateLimitExceededException(result.retryAfterSeconds(),
                    "MEDIA_PRESIGN_RATE_LIMIT_EXCEEDED",
                    "Too many media upload requests. Please try again later.");
        }
    }
}
