package com.ykskocluk.demo.security.ratelimit;

import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Per-user abuse controls shared by authenticated HTTP and STOMP actions. The backing store is
 * intentionally abstract; the current single-instance deployment uses the JVM-local store, while
 * a multi-instance deployment must provide a shared {@link RateLimitStore} implementation.
 */
@Service
public class AuthenticatedActionRateLimitService {

    private final RateLimitStore store;
    private final RateLimitProperties properties;

    public AuthenticatedActionRateLimitService(RateLimitStore store, RateLimitProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    public void checkMessageSend(Long userId) {
        check("message:send", userId, properties.getMessageSend(),
                "MESSAGE_RATE_LIMIT_EXCEEDED", "Çok fazla mesaj gönderdiniz. Lütfen daha sonra tekrar deneyin.");
    }

    public void checkTrialCreate(Long userId) {
        check("trial:create", userId, properties.getTrialCreate(),
                "TRIAL_RATE_LIMIT_EXCEEDED", "Çok fazla deneme görüşmesi talebi oluşturdunuz.");
    }

    public void checkReportCreate(Long userId) {
        check("report:create", userId, properties.getReportCreate(),
                "REPORT_RATE_LIMIT_EXCEEDED", "Çok fazla bildirim oluşturdunuz. Lütfen daha sonra tekrar deneyin.");
    }

    public void checkMediaComplete(Long userId) {
        check("media:complete", userId, properties.getMediaComplete(),
                "MEDIA_COMPLETE_RATE_LIMIT_EXCEEDED", "Çok fazla yükleme tamamlama isteği gönderdiniz.");
    }

    private void check(String action, Long userId, RateLimitProperties.UserLimitRule rule,
                       String errorCode, String message) {
        if (!properties.isEnabled()) {
            return;
        }
        String key = "yks:%s:rate-limit:%s:user:%d"
                .formatted(properties.getEnvironment(), action, userId);
        RateLimitResult result = store.consume(
                key, rule.getUserLimit(), Duration.ofSeconds(rule.getWindowSeconds()));
        if (!result.allowed()) {
            throw new RateLimitExceededException(result.retryAfterSeconds(), errorCode, message);
        }
    }
}
