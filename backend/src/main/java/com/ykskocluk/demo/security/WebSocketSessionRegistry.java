package com.ykskocluk.demo.security;

import com.ykskocluk.demo.config.WebSocketSecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Central registry for authenticated STOMP transport, connection and subscription lifecycle. */
@Component
public class WebSocketSessionRegistry {
    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionRegistry.class);

    private final WebSocketSecurityProperties properties;
    private final Map<String, WebSocketSession> transports = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionUsers = new HashMap<>();
    private final Map<Long, Set<String>> userSessions = new HashMap<>();
    private final Map<String, Set<String>> subscriptions = new HashMap<>();

    public WebSocketSessionRegistry(WebSocketSecurityProperties properties) {
        this.properties = properties;
    }

    public void registerTransport(WebSocketSession session) {
        transports.put(session.getId(), session);
    }

    public synchronized boolean bindAuthenticatedSession(String sessionId, Long userId) {
        Long existingUser = sessionUsers.get(sessionId);
        if (userId.equals(existingUser)) return true;
        if (existingUser != null) releaseCounters(sessionId, existingUser);

        Set<String> sessions = userSessions.computeIfAbsent(userId, ignored -> new HashSet<>());
        if (sessions.size() >= properties.maxConnectionsPerUser()) return false;
        sessions.add(sessionId);
        sessionUsers.put(sessionId, userId);
        return true;
    }

    public synchronized boolean addSubscription(String sessionId, String subscriptionId) {
        Set<String> sessionSubscriptions = subscriptions.computeIfAbsent(sessionId, ignored -> new HashSet<>());
        if (sessionSubscriptions.contains(subscriptionId)) return true;
        if (sessionSubscriptions.size() >= properties.maxSubscriptionsPerSession()) return false;
        sessionSubscriptions.add(subscriptionId);
        return true;
    }

    public synchronized void removeSubscription(String sessionId, String subscriptionId) {
        Set<String> sessionSubscriptions = subscriptions.get(sessionId);
        if (sessionSubscriptions == null) return;
        sessionSubscriptions.remove(subscriptionId);
        if (sessionSubscriptions.isEmpty()) subscriptions.remove(sessionId);
    }

    public void releaseSession(String sessionId) {
        transports.remove(sessionId);
        synchronized (this) {
            Long userId = sessionUsers.remove(sessionId);
            if (userId != null) releaseCounters(sessionId, userId);
            subscriptions.remove(sessionId);
        }
    }

    public void closeSession(String sessionId, String reason) {
        WebSocketSession session = transports.get(sessionId);
        releaseSession(sessionId);
        if (session == null || !session.isOpen()) return;
        try {
            session.close(new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), safeReason(reason)));
        } catch (IOException exception) {
            log.debug("WebSocket session close failed; sessionId={}", sessionId, exception);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void invalidateUser(WebSocketSessionsInvalidatedEvent event) {
        List<String> sessionIds;
        synchronized (this) {
            sessionIds = new ArrayList<>(userSessions.getOrDefault(event.userId(), Set.of()));
        }
        sessionIds.forEach(sessionId -> closeSession(sessionId, "Authentication state changed"));
    }

    synchronized int connectionCount(Long userId) {
        return userSessions.getOrDefault(userId, Set.of()).size();
    }

    synchronized int subscriptionCount(String sessionId) {
        return subscriptions.getOrDefault(sessionId, Set.of()).size();
    }

    private void releaseCounters(String sessionId, Long userId) {
        Set<String> sessions = userSessions.get(userId);
        if (sessions == null) return;
        sessions.remove(sessionId);
        if (sessions.isEmpty()) userSessions.remove(userId);
    }

    private static String safeReason(String reason) {
        if (reason == null || reason.isBlank()) return "Policy violation";
        return reason.length() <= 80 ? reason : reason.substring(0, 80);
    }
}
