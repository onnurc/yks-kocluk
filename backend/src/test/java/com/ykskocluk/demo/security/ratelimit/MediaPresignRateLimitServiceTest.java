package com.ykskocluk.demo.security.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaPresignRateLimitServiceTest {
    @Mock RateLimitStore store;
    RateLimitProperties properties;
    MediaPresignRateLimitService service;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        properties.setEnvironment("test");
        properties.setMediaPresign(new RateLimitProperties.UserLimitRule(12, 600));
        service = new MediaPresignRateLimitService(store, properties);
    }

    @Test
    void allowsRequestWithinUserQuota() {
        when(store.consume("yks:test:rate-limit:media:presign:user:42", 12, Duration.ofMinutes(10)))
                .thenReturn(new RateLimitResult(true, 11, 0));

        service.check(42L);

        verify(store).consume("yks:test:rate-limit:media:presign:user:42", 12, Duration.ofMinutes(10));
    }

    @Test
    void exceededQuotaUsesStableMedia429Code() {
        when(store.consume("yks:test:rate-limit:media:presign:user:42", 12, Duration.ofMinutes(10)))
                .thenReturn(new RateLimitResult(false, 0, 73));

        RateLimitExceededException error = catchThrowableOfType(
                RateLimitExceededException.class, () -> service.check(42L));

        assertThat(error.getErrorCode()).isEqualTo("MEDIA_PRESIGN_RATE_LIMIT_EXCEEDED");
        assertThat(error.getRetryAfterSeconds()).isEqualTo(73);
    }
}
