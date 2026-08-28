package com.ykskocluk.demo.security.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticatedActionRateLimitServiceTest {

    private final RateLimitStore store = mock(RateLimitStore.class);
    private final RateLimitProperties properties = new RateLimitProperties();
    private AuthenticatedActionRateLimitService service;

    @BeforeEach
    void setUp() {
        properties.setEnvironment("test");
        service = new AuthenticatedActionRateLimitService(store, properties);
    }

    @Test
    void restAndStompMessageCallsShareTheSamePerUserBucket() {
        when(store.consume(eq("yks:test:rate-limit:message:send:user:7"), eq(60), eq(Duration.ofSeconds(60))))
                .thenReturn(new RateLimitResult(true, 59, 0));

        service.checkMessageSend(7L);

        verify(store).consume("yks:test:rate-limit:message:send:user:7", 60, Duration.ofSeconds(60));
    }

    @Test
    void deniedActionUsesSpecificErrorCodeAndRetryAfter() {
        when(store.consume(eq("yks:test:rate-limit:trial:create:user:7"), eq(5), eq(Duration.ofHours(1))))
                .thenReturn(new RateLimitResult(false, 0, 31));

        RateLimitExceededException error = catchThrowableOfType(RateLimitExceededException.class,
                () -> service.checkTrialCreate(7L));

        assertThat(error.getErrorCode()).isEqualTo("TRIAL_RATE_LIMIT_EXCEEDED");
        assertThat(error.getRetryAfterSeconds()).isEqualTo(31);
    }
}
