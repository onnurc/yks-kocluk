package com.ykskocluk.demo.security.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    private String environment = "local";
    private boolean trustProxyHeaders = false;
    private LimitRule login = new LimitRule(20, 5, 60);
    private LimitRule register = new LimitRule(5, 0, 60);
    private RefreshLimitRule refresh = new RefreshLimitRule(30, 30, 60);

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
}
