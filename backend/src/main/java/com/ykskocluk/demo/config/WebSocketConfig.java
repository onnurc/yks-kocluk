package com.ykskocluk.demo.config;

import com.ykskocluk.demo.security.StompAuthChannelInterceptor;
import com.ykskocluk.demo.security.WebSocketSessionRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP-over-WebSocket for chat (Phase 5b). Auth happens in the CONNECT frame via
 * {@link StompAuthChannelInterceptor} (the HTTP handshake at {@code /ws} is permitted in
 * SecurityConfig — no token is on the handshake; it travels in the STOMP CONNECT frame).
 * The simple in-memory broker is sufficient for the MVP (CLAUDE.md: no Redis relay).
 */
@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(WebSocketSecurityProperties.class)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor authChannelInterceptor;
    private final CorsProperties corsProperties;
    private final WebSocketSessionRegistry sessionRegistry;
    private final WebSocketSecurityProperties securityProperties;
    private final TaskScheduler heartbeatScheduler;

    public WebSocketConfig(StompAuthChannelInterceptor authChannelInterceptor,
                           CorsProperties corsProperties,
                           WebSocketSessionRegistry sessionRegistry,
                           WebSocketSecurityProperties securityProperties,
                           @Qualifier("webSocketHeartbeatScheduler") TaskScheduler heartbeatScheduler) {
        this.authChannelInterceptor = authChannelInterceptor;
        this.corsProperties = corsProperties;
        this.sessionRegistry = sessionRegistry;
        this.securityProperties = securityProperties;
        this.heartbeatScheduler = heartbeatScheduler;
    }

    // Reads the same CorsProperties bean SecurityConfig's REST CORS uses, so there is one
    // allowlist (app.cors.allowed-origins), not two to keep in sync.
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] allowedOrigins = corsProperties.allowedOrigins().toArray(String[]::new);
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins);
        // SockJS fallback for browsers that can't open a raw WebSocket.
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins).withSockJS();
    }

    /**
     * {@code /topic} carries per-conversation broadcast; {@code /queue} carries the user-scoped
     * notification channel ({@code /user/queue/notifications} — nav badge and inbox updates from
     * any page). {@code /queue} is not optional there: Spring rewrites a {@code /user/**}
     * subscription into {@code /queue/notifications-user{sessionId}} and hands it to the broker,
     * so without {@code /queue} in this list the simple broker owns no such destination and the
     * push is silently dropped. The default {@code /user} prefix is left as-is.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setTaskScheduler(heartbeatScheduler)
                .setHeartbeatValue(new long[]{securityProperties.serverHeartbeatMillis(),
                        securityProperties.clientHeartbeatMillis()});
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(this::trackTransportSession);
    }

    private WebSocketHandler trackTransportSession(WebSocketHandler delegate) {
        return new WebSocketHandlerDecorator(delegate) {
            @Override
            public void afterConnectionEstablished(org.springframework.web.socket.WebSocketSession session)
                    throws Exception {
                sessionRegistry.registerTransport(session);
                try {
                    super.afterConnectionEstablished(session);
                } catch (Exception exception) {
                    sessionRegistry.releaseSession(session.getId());
                    throw exception;
                }
            }

            @Override
            public void afterConnectionClosed(org.springframework.web.socket.WebSocketSession session,
                                              org.springframework.web.socket.CloseStatus closeStatus)
                    throws Exception {
                try {
                    super.afterConnectionClosed(session, closeStatus);
                } finally {
                    sessionRegistry.releaseSession(session.getId());
                }
            }
        };
    }
}
