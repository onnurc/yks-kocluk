package com.ykskocluk.demo.security.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    private String environment = "local";
    private Store store = Store.REDIS;
    private FailurePolicy failurePolicy = FailurePolicy.IN_MEMORY_FALLBACK;
    private boolean trustProxyHeaders = false;
    private List<String> trustedProxyCidrs = new ArrayList<>();
    private LimitRule login = new LimitRule(20, 5, 60);
    private LimitRule register = new LimitRule(20, 3, 3600);
    private RefreshLimitRule refresh = new RefreshLimitRule(30, 30, 60);
    private LimitRule oauth2Exchange = new LimitRule(30, 0, 60);
    private LimitRule forgotPassword = new LimitRule(10, 3, 900);
    private LimitRule resetPassword = new LimitRule(20, 5, 900);
    private LimitRule changePassword = new LimitRule(10, 5, 900);
    private LimitRule emailVerification = new LimitRule(30, 10, 600);
    private LimitRule emailVerificationResend = new LimitRule(20, 5, 3600);
    private LimitRule coachApplication = new LimitRule(10, 3, 3600);
    private UserLimitRule mediaPresign = new UserLimitRule(12, 600);
    private UserLimitRule messageSend = new UserLimitRule(60, 60);
    private UserLimitRule trialCreate = new UserLimitRule(5, 3600);
    private UserLimitRule reportCreate = new UserLimitRule(10, 3600);
    private UserLimitRule mediaComplete = new UserLimitRule(30, 600);
    private UserLimitRule checkoutCreate = new UserLimitRule(5, 3600);

    public enum Store { IN_MEMORY, REDIS }
    public enum FailurePolicy { IN_MEMORY_FALLBACK, FAIL_CLOSED }

    @Getter
    @Setter
    public static class LimitRule {
        private int ipLimit;
        private int identifierLimit;
        private int windowSeconds;

        public LimitRule() {}
        public LimitRule(int ipLimit, int identifierLimit, int windowSeconds) {
            this.ipLimit = ipLimit;
            this.identifierLimit = identifierLimit;
            this.windowSeconds = windowSeconds;
        }
    }

    @Getter
    @Setter
    public static class RefreshLimitRule {
        private int ipLimit;
        private int tokenLimit;
        private int windowSeconds;

        public RefreshLimitRule() {}
        public RefreshLimitRule(int ipLimit, int tokenLimit, int windowSeconds) {
            this.ipLimit = ipLimit;
            this.tokenLimit = tokenLimit;
            this.windowSeconds = windowSeconds;
        }
    }

    @Getter
    @Setter
    public static class UserLimitRule {
        private int userLimit;
        private int windowSeconds;

        public UserLimitRule() {}
        public UserLimitRule(int userLimit, int windowSeconds) {
            this.userLimit = userLimit;
            this.windowSeconds = windowSeconds;
        }
    }
}
