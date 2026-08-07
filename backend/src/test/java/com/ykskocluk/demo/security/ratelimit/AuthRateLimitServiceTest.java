package com.ykskocluk.demo.security.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthRateLimitServiceTest {

    private MutableClock clock;
    private InMemoryRateLimitStore store;
    private RateLimitProperties properties;
    private ClientIpResolver ipResolver;
    private AuthRateLimitService rateLimitService;

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
        clock = new MutableClock(Instant.parse("2026-07-18T12:00:00Z"), ZoneId.of("UTC"));
        store = new InMemoryRateLimitStore(clock);
        properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.setEnvironment("local");

        // Defaults to test specific rate limiter configurations:
        properties.getLogin().setIpLimit(2);
        properties.getLogin().setIdentifierLimit(1);
        properties.getLogin().setWindowSeconds(60);

        properties.getRegister().setIpLimit(1);
        properties.getRegister().setIdentifierLimit(1);
        properties.getRegister().setWindowSeconds(60);

        properties.getRefresh().setIpLimit(2);
        properties.getRefresh().setTokenLimit(1);
        properties.getRefresh().setWindowSeconds(60);
        properties.getForgotPassword().setIpLimit(10);
        properties.getForgotPassword().setIdentifierLimit(1);
        properties.getForgotPassword().setWindowSeconds(60);
        properties.getResetPassword().setIpLimit(10);
        properties.getResetPassword().setIdentifierLimit(1);
        properties.getResetPassword().setWindowSeconds(60);
        properties.getChangePassword().setIpLimit(10);
        properties.getChangePassword().setIdentifierLimit(1);
        properties.getChangePassword().setWindowSeconds(60);
        properties.getEmailVerification().setIpLimit(10);
        properties.getEmailVerification().setIdentifierLimit(2);
        properties.getEmailVerification().setWindowSeconds(600);
        properties.getEmailVerificationResend().setIpLimit(10);
        properties.getEmailVerificationResend().setIdentifierLimit(1);
        properties.getEmailVerificationResend().setWindowSeconds(3600);

        ipResolver = new ClientIpResolver(properties);
        rateLimitService = new AuthRateLimitService(store, properties, ipResolver);
    }

    @Test
    void checkLogin_ipLimitExceeded_throwsRateLimitExceeded() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.1");

        // Success 1 and 2
        rateLimitService.checkLogin("user1@example.com", request);
        rateLimitService.checkLogin("user2@example.com", request);

        // Third hit triggers IP block (limit is 2)
        assertThatThrownBy(() -> rateLimitService.checkLogin("user3@example.com", request))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Too many requests. Please try again later.")
                .satisfies(ex -> {
                    RateLimitExceededException rle = (RateLimitExceededException) ex;
                    assertThat(rle.getRetryAfterSeconds()).isEqualTo(60L);
                });
    }

    @Test
    void checkLogin_identifierLimitExceeded_throwsRateLimitExceeded() {
        MockHttpServletRequest request1 = new MockHttpServletRequest();
        request1.setRemoteAddr("192.168.1.1");

        MockHttpServletRequest request2 = new MockHttpServletRequest();
        request2.setRemoteAddr("192.168.1.2");

        // Limit for identifier is 1
        rateLimitService.checkLogin("USER@example.com ", request1); // Normalizes to user@example.com

        // Second hit for the same normalized email from another IP triggers identifier limit
        assertThatThrownBy(() -> rateLimitService.checkLogin(" user@example.com", request2))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(ex -> {
                    RateLimitExceededException rle = (RateLimitExceededException) ex;
                    assertThat(rle.getRetryAfterSeconds()).isEqualTo(60L);
                });
    }

    @Test
    void checkRegister_ipLimitExceeded_throwsRateLimitExceeded() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.1");

        // First allowed (limit is 1)
        rateLimitService.checkRegister("student@example.com", request);

        // Second blocked
        assertThatThrownBy(() -> rateLimitService.checkRegister("other@example.com", request))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void checkRegister_identifierLimitExceeded_throwsRateLimitExceeded() {
        MockHttpServletRequest request1 = new MockHttpServletRequest();
        request1.setRemoteAddr("192.168.1.1");

        MockHttpServletRequest request2 = new MockHttpServletRequest();
        request2.setRemoteAddr("192.168.1.2");

        // First allowed (identifier limit is 1)
        rateLimitService.checkRegister("student@example.com", request1);

        // Second blocked for same email from different IP
        assertThatThrownBy(() -> rateLimitService.checkRegister("student@example.com", request2))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void checkRegister_emailNormalization() {
        MockHttpServletRequest request1 = new MockHttpServletRequest();
        request1.setRemoteAddr("192.168.1.1");

        MockHttpServletRequest request2 = new MockHttpServletRequest();
        request2.setRemoteAddr("192.168.1.2");

        // First allowed
        rateLimitService.checkRegister(" STUDENT@example.com ", request1);

        // Second with different casing/spacing is blocked
        assertThatThrownBy(() -> rateLimitService.checkRegister("student@example.com", request2))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void checkRegister_differentEmailsSameIp_stillRespectsIpLimit() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.1");

        // First allowed (IP limit is 1)
        rateLimitService.checkRegister("student1@example.com", request);

        // Second with different email is blocked by IP limit
        assertThatThrownBy(() -> rateLimitService.checkRegister("student2@example.com", request))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void checkRefresh_ipLimitExceeded_throwsRateLimitExceeded() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.1");

        // Limit is 2
        rateLimitService.checkRefresh("token1", request);
        rateLimitService.checkRefresh("token2", request);

        assertThatThrownBy(() -> rateLimitService.checkRefresh("token3", request))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void checkRefresh_tokenLimitExceeded_throwsRateLimitExceeded() {
        MockHttpServletRequest request1 = new MockHttpServletRequest();
        request1.setRemoteAddr("192.168.1.1");

        MockHttpServletRequest request2 = new MockHttpServletRequest();
        request2.setRemoteAddr("192.168.1.2");

        // Limit is 1
        rateLimitService.checkRefresh("token-abc", request1);

        assertThatThrownBy(() -> rateLimitService.checkRefresh("token-abc", request2))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void check_whenDisabled_bypassesCheck() {
        properties.setEnabled(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.1");

        // All allowed even though limits are 1
        rateLimitService.checkRegister("student@example.com", request);
        rateLimitService.checkRegister("student@example.com", request);
        rateLimitService.checkRegister("other@example.com", request);
    }

    @Test
    void verificationResend_hasIndependentHourlyUserLimit() {
        MockHttpServletRequest firstIp = new MockHttpServletRequest();
        firstIp.setRemoteAddr("192.168.10.1");
        MockHttpServletRequest secondIp = new MockHttpServletRequest();
        secondIp.setRemoteAddr("192.168.10.2");

        rateLimitService.checkEmailVerificationResend(42L, firstIp);
        assertThatThrownBy(() -> rateLimitService.checkEmailVerificationResend(42L, secondIp))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(error -> assertThat(((RateLimitExceededException) error).getRetryAfterSeconds())
                        .isEqualTo(3600L));
    }

    @Test
    void forgotPassword_normalizedEmailLimitDoesNotDependOnAccountExistence() {
        MockHttpServletRequest first = new MockHttpServletRequest(); first.setRemoteAddr("192.168.1.1");
        MockHttpServletRequest second = new MockHttpServletRequest(); second.setRemoteAddr("192.168.1.2");
        rateLimitService.checkForgotPassword(" USER@example.com ", first);
        assertThatThrownBy(() -> rateLimitService.checkForgotPassword("user@example.com", second))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void resetAndChangePassword_haveIndependentAttemptLimits() {
        MockHttpServletRequest first = new MockHttpServletRequest(); first.setRemoteAddr("192.168.1.1");
        MockHttpServletRequest second = new MockHttpServletRequest(); second.setRemoteAddr("192.168.1.2");
        rateLimitService.checkResetPassword("token", first);
        assertThatThrownBy(() -> rateLimitService.checkResetPassword("token", second)).isInstanceOf(RateLimitExceededException.class);
        rateLimitService.checkChangePassword(42L, first);
        assertThatThrownBy(() -> rateLimitService.checkChangePassword(42L, second)).isInstanceOf(RateLimitExceededException.class);
    }
}
