package com.ykskocluk.demo.security;

import com.ykskocluk.demo.config.WebSocketSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketSessionRegistryTest {
    private WebSocketSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WebSocketSessionRegistry(new WebSocketSecurityProperties(2, 2, 10_000, 10_000));
    }

    @Test
    void connectionAndSubscriptionLimitsAreBoundedAndReleased() {
        assertThat(registry.bindAuthenticatedSession("s1", 7L)).isTrue();
        assertThat(registry.bindAuthenticatedSession("s2", 7L)).isTrue();
        assertThat(registry.bindAuthenticatedSession("s3", 7L)).isFalse();
        assertThat(registry.connectionCount(7L)).isEqualTo(2);

        assertThat(registry.addSubscription("s1", "a")).isTrue();
        assertThat(registry.addSubscription("s1", "b")).isTrue();
        assertThat(registry.addSubscription("s1", "c")).isFalse();
        registry.removeSubscription("s1", "a");
        assertThat(registry.addSubscription("s1", "c")).isTrue();

        registry.releaseSession("s1");
        assertThat(registry.connectionCount(7L)).isEqualTo(1);
        assertThat(registry.subscriptionCount("s1")).isZero();
        assertThat(registry.bindAuthenticatedSession("reconnect", 7L)).isTrue();
    }

    @Test
    void invalidationClosesAndReleasesEveryActiveUserTransport() throws IOException {
        WebSocketSession first = session("s1");
        WebSocketSession second = session("s2");
        registry.registerTransport(first);
        registry.registerTransport(second);
        registry.bindAuthenticatedSession("s1", 7L);
        registry.bindAuthenticatedSession("s2", 7L);

        registry.invalidateUser(new WebSocketSessionsInvalidatedEvent(7L));

        verify(first).close(any(CloseStatus.class));
        verify(second).close(any(CloseStatus.class));
        assertThat(registry.connectionCount(7L)).isZero();
    }

    private WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
