package com.ykskocluk.demo.security.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {
    private final RateLimitProperties properties;

    public ClientIpResolver(RateLimitProperties properties) {
        this.properties = properties;
    }

    public String resolveIp(HttpServletRequest request) {
        if (properties.isTrustProxyHeaders() && isTrustedProxy(request.getRemoteAddr())) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                String[] ips = xForwardedFor.split(",");
                if (ips.length > 0) {
                    String ip = ips[0].trim();
                    if (!ip.isBlank()) {
                        return ip;
                    }
                }
            }
        }
        return request.getRemoteAddr();
    }

    private boolean isTrustedProxy(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) return false;
        return properties.getTrustedProxyCidrs().stream().anyMatch(cidr -> {
            try {
                return new IpAddressMatcher(cidr).matches(remoteAddress);
            } catch (IllegalArgumentException invalidConfiguration) {
                throw new IllegalStateException("Invalid trusted proxy CIDR: " + cidr, invalidConfiguration);
            }
        });
    }
}
