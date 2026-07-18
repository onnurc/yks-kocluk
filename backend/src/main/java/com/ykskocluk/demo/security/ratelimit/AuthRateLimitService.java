package com.ykskocluk.demo.security.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AuthRateLimitService {
    private final RateLimitStore rateLimitStore;
    private final RateLimitProperties properties;
    private final ClientIpResolver ipResolver;

    public AuthRateLimitService(RateLimitStore rateLimitStore,
                                 RateLimitProperties properties,
                                 ClientIpResolver ipResolver) {
        this.rateLimitStore = rateLimitStore;
        this.properties = properties;
        this.ipResolver = ipResolver;
    }

    public void checkLogin(String email, HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return;
        }

        String ip = ipResolver.resolveIp(request);
        String ipHash = sha256(ip);
        String env = properties.getEnvironment();

        // 1. IP rate limit check
        String ipKey = String.format("yks:%s:rate-limit:auth:login:ip:%s", env, ipHash);
        RateLimitProperties.LimitRule rule = properties.getLogin();
        RateLimitResult ipResult = rateLimitStore.consume(ipKey, rule.getIpLimit(), Duration.ofSeconds(rule.getWindowSeconds()));
        if (!ipResult.allowed()) {
            throw new RateLimitExceededException(ipResult.retryAfterSeconds());
        }

        // 2. Email rate limit check
        if (email != null && !email.isBlank()) {
            String identifierHash = sha256(email.trim().toLowerCase());
            String emailKey = String.format("yks:%s:rate-limit:auth:login:identifier:%s", env, identifierHash);
            RateLimitResult emailResult = rateLimitStore.consume(emailKey, rule.getIdentifierLimit(), Duration.ofSeconds(rule.getWindowSeconds()));
            if (!emailResult.allowed()) {
                throw new RateLimitExceededException(emailResult.retryAfterSeconds());
            }
        }
    }

    public void checkRegister(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return;
        }

        String ip = ipResolver.resolveIp(request);
        String ipHash = sha256(ip);
        String env = properties.getEnvironment();

        String ipKey = String.format("yks:%s:rate-limit:auth:register:ip:%s", env, ipHash);
        RateLimitProperties.LimitRule rule = properties.getRegister();
        RateLimitResult ipResult = rateLimitStore.consume(ipKey, rule.getIpLimit(), Duration.ofSeconds(rule.getWindowSeconds()));
        if (!ipResult.allowed()) {
            throw new RateLimitExceededException(ipResult.retryAfterSeconds());
        }
    }

    public void checkRefresh(String refreshToken, HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return;
        }

        String ip = ipResolver.resolveIp(request);
        String ipHash = sha256(ip);
        String env = properties.getEnvironment();

        // 1. IP limit check
        String ipKey = String.format("yks:%s:rate-limit:auth:refresh:ip:%s", env, ipHash);
        RateLimitProperties.RefreshLimitRule rule = properties.getRefresh();
        RateLimitResult ipResult = rateLimitStore.consume(ipKey, rule.getIpLimit(), Duration.ofSeconds(rule.getWindowSeconds()));
        if (!ipResult.allowed()) {
            throw new RateLimitExceededException(ipResult.retryAfterSeconds());
        }

        // 2. Token fingerprint limit check
        if (refreshToken != null && !refreshToken.isBlank()) {
            String tokenFingerprint = sha256(refreshToken);
            String tokenKey = String.format("yks:%s:rate-limit:auth:refresh:token:%s", env, tokenFingerprint);
            RateLimitResult tokenResult = rateLimitStore.consume(tokenKey, rule.getTokenLimit(), Duration.ofSeconds(rule.getWindowSeconds()));
            if (!tokenResult.allowed()) {
                throw new RateLimitExceededException(tokenResult.retryAfterSeconds());
            }
        }
    }

    public void checkOAuth2Exchange(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return;
        }

        String ip = ipResolver.resolveIp(request);
        String ipHash = sha256(ip);
        String env = properties.getEnvironment();

        String ipKey = String.format("yks:%s:rate-limit:auth:oauth2-exchange:ip:%s", env, ipHash);
        RateLimitProperties.LimitRule rule = properties.getOauth2Exchange();
        RateLimitResult ipResult = rateLimitStore.consume(ipKey, rule.getIpLimit(), Duration.ofSeconds(rule.getWindowSeconds()));
        if (!ipResult.allowed()) {
            throw new RateLimitExceededException(ipResult.retryAfterSeconds());
        }
    }

    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void cleanExpiredEntries() {
        // Clean entries older than 10 minutes (600 seconds)
        rateLimitStore.cleanup(Duration.ofMinutes(10));
    }

    private String sha256(String value) {
        if (value == null) {
            return "";
        }
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
