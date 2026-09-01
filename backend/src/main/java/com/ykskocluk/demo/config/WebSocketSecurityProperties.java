package com.ykskocluk.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.websocket-security")
public record WebSocketSecurityProperties(
        int maxConnectionsPerUser,
        int maxSubscriptionsPerSession,
        long serverHeartbeatMillis,
        long clientHeartbeatMillis
) {
    public WebSocketSecurityProperties {
        if (maxConnectionsPerUser < 1) {
            throw new IllegalArgumentException("app.websocket-security.max-connections-per-user must be positive");
        }
        if (maxSubscriptionsPerSession < 1) {
            throw new IllegalArgumentException("app.websocket-security.max-subscriptions-per-session must be positive");
        }
        if (serverHeartbeatMillis < 1 || clientHeartbeatMillis < 1) {
            throw new IllegalArgumentException("WebSocket heartbeat intervals must be positive");
        }
    }
}
