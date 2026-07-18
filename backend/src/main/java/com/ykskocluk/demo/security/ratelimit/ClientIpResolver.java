package com.ykskocluk.demo.security.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {
    private final RateLimitProperties properties;

    public ClientIpResolver(RateLimitProperties properties) {
        this.properties = properties;
    }

    public String resolveIp(HttpServletRequest request) {
        if (properties.isTrustProxyHeaders()) {
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
}
