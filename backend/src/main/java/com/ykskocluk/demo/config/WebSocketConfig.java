package com.ykskocluk.demo.config;

import com.ykskocluk.demo.security.StompAuthChannelInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP-over-WebSocket for chat (Phase 5b). Auth happens in the CONNECT frame via
 * {@link StompAuthChannelInterceptor} (the HTTP handshake at {@code /ws} is permitted in
 * SecurityConfig — no token is on the handshake; it travels in the STOMP CONNECT frame).
 * The simple in-memory broker is sufficient for the MVP (CLAUDE.md: no Redis relay).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor authChannelInterceptor;
    private final CorsProperties corsProperties;

    public WebSocketConfig(StompAuthChannelInterceptor authChannelInterceptor,
                           CorsProperties corsProperties) {
        this.authChannelInterceptor = authChannelInterceptor;
        this.corsProperties = corsProperties;
    }

    // Same allowlist as SecurityConfig's REST CORS — keep the two in sync.
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] allowedOrigins = corsProperties.allowedOrigins().toArray(String[]::new);
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins);
        // SockJS fallback for browsers that can't open a raw WebSocket.
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins).withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }
}
