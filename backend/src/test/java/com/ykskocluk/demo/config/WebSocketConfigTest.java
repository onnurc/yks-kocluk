package com.ykskocluk.demo.config;

import com.ykskocluk.demo.security.StompAuthChannelInterceptor;
import org.junit.jupiter.api.Test;
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
}
