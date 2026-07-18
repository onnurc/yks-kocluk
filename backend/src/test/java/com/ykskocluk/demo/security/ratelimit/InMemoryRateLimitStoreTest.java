package com.ykskocluk.demo.security.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimitStoreTest {

    private MutableClock mutableClock;
    private InMemoryRateLimitStore rateLimitStore;

    private static class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void advanceBySeconds(long seconds) {
            this.instant = this.instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @BeforeEach
    void setUp() {
        mutableClock = new MutableClock(Instant.parse("2026-07-18T12:00:00Z"), ZoneId.of("UTC"));
        rateLimitStore = new InMemoryRateLimitStore(mutableClock);
    }

    @Test
    void consume_underLimit_allowsAndDecrementsRemaining() {
        String key = "test-key";
        Duration window = Duration.ofMinutes(1);

        RateLimitResult res1 = rateLimitStore.consume(key, 3, window);
        assertThat(res1.allowed()).isTrue();
        assertThat(res1.remaining()).isEqualTo(2);
        assertThat(res1.retryAfterSeconds()).isZero();

        RateLimitResult res2 = rateLimitStore.consume(key, 3, window);
        assertThat(res2.allowed()).isTrue();
        assertThat(res2.remaining()).isEqualTo(1);
        assertThat(res2.retryAfterSeconds()).isZero();

        RateLimitResult res3 = rateLimitStore.consume(key, 3, window);
        assertThat(res3.allowed()).isTrue();
        assertThat(res3.remaining()).isEqualTo(0);
        assertThat(res3.retryAfterSeconds()).isZero();
    }

    @Test
    void consume_overLimit_blocksAndReturnsCorrectRetryAfter() {
        String key = "test-key";
        Duration window = Duration.ofSeconds(60);

        // Fill up to limit (3)
        rateLimitStore.consume(key, 3, window);
        rateLimitStore.consume(key, 3, window);
        rateLimitStore.consume(key, 3, window);

        // Consume 4th time (over limit)
        RateLimitResult blocked = rateLimitStore.consume(key, 3, window);
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.remaining()).isZero();
        assertThat(blocked.retryAfterSeconds()).isEqualTo(60L); // Since no time has passed

        // Advance clock by 15 seconds
        mutableClock.advanceBySeconds(15);
        RateLimitResult blockedAgain = rateLimitStore.consume(key, 3, window);
        assertThat(blockedAgain.allowed()).isFalse();
        assertThat(blockedAgain.retryAfterSeconds()).isEqualTo(45L); // 60 - 15 = 45 remaining
    }

    @Test
    void consume_windowExpires_resetsLimit() {
        String key = "test-key";
        Duration window = Duration.ofSeconds(60);

        rateLimitStore.consume(key, 1, window);

        // Check blocked
        RateLimitResult blocked = rateLimitStore.consume(key, 1, window);
        assertThat(blocked.allowed()).isFalse();

        // Advance past window
        mutableClock.advanceBySeconds(61);

        // Reset works
        RateLimitResult allowed = rateLimitStore.consume(key, 1, window);
        assertThat(allowed.allowed()).isTrue();
        assertThat(allowed.remaining()).isZero();
    }

    @Test
    void consume_differentKeys_doNotInterfere() {
        Duration window = Duration.ofMinutes(1);

        RateLimitResult res1 = rateLimitStore.consume("key-1", 1, window);
        RateLimitResult res2 = rateLimitStore.consume("key-2", 1, window);

        assertThat(res1.allowed()).isTrue();
        assertThat(res2.allowed()).isTrue();
    }

    @Test
    void cleanup_removesExpiredEntriesOnly() {
        Duration window = Duration.ofSeconds(60);

        rateLimitStore.consume("active-key", 5, window);
        rateLimitStore.consume("expired-key", 5, window);

        // Advance clock so only "expired-key" is past max age (e.g., 10 mins / 600s)
        mutableClock.advanceBySeconds(601);
        rateLimitStore.consume("active-key", 5, window); // Fresh hit on active-key

        // Clean up entries older than 10 minutes
        rateLimitStore.cleanup(Duration.ofMinutes(10));

        // expired-key should have been removed from internal state (meaning a new hit starts a fresh bucket)
        RateLimitResult resExpired = rateLimitStore.consume("expired-key", 5, window);
        assertThat(resExpired.remaining()).isEqualTo(4); // Reset and first consume
    }

    @Test
    void consume_concurrentAccess_limitEnforced() throws InterruptedException {
        String key = "concurrent-key";
        Duration window = Duration.ofMinutes(1);
        int limit = 50;
        int threadsCount = 100;

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadsCount);
        AtomicInteger allowedCount = new AtomicInteger(0);

        for (int i = 0; i < threadsCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    RateLimitResult res = rateLimitStore.consume(key, limit, window);
                    if (res.allowed()) {
                        allowedCount.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        finishLatch.await();
        executor.shutdown();

        assertThat(allowedCount.get()).isEqualTo(limit);
    }
}
