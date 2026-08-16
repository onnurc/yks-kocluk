package com.ykskocluk.demo.config;

import com.ykskocluk.demo.security.StompAuthChannelInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketConfigTest {

    @Test
    void websocketEndpointsUseTheSharedConfiguredOrigins() {
        StompAuthChannelInterceptor interceptor = mock(StompAuthChannelInterceptor.class);
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration registration = mock(StompWebSocketEndpointRegistration.class);
        when(registry.addEndpoint("/ws")).thenReturn(registration);
        when(registration.setAllowedOrigins(
                "http://localhost:5173", "https://frontend.example.com")).thenReturn(registration);

        WebSocketConfig config = new WebSocketConfig(interceptor, new CorsProperties(List.of(
                "http://localhost:5173", "https://frontend.example.com")));

        config.registerStompEndpoints(registry);

        verify(registration, times(2)).setAllowedOrigins(
                "http://localhost:5173", "https://frontend.example.com");
        verify(registration).withSockJS();
    }

    /**
     * {@code /queue} has to be a broker destination or the user-scoped notification channel dies
     * silently: Spring rewrites a {@code /user/**} subscription to {@code /queue/...-user{id}} and
     * hands it to the broker, which drops it if it owns no such prefix. Nothing logs a warning, so
     * the failure would only show up as a badge that never moves.
     */
    @Test
    void brokerServesBothTheConversationTopicsAndTheUserQueue() {
        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);
        WebSocketConfig config = new WebSocketConfig(mock(StompAuthChannelInterceptor.class),
                new CorsProperties(List.of("http://localhost:5173")));

        config.configureMessageBroker(registry);

        verify(registry).enableSimpleBroker("/topic", "/queue");
        verify(registry).setApplicationDestinationPrefixes("/app");
    }
}
