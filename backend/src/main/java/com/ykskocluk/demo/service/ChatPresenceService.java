package com.ykskocluk.demo.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Single-instance STOMP presence registry. Sessions, rather than booleans, are counted so closing
 * one tab cannot mark a user offline while another authenticated tab/device remains connected.
 */
@Service
public class ChatPresenceService {

    private final Map<String, Long> usersBySession = new HashMap<>();
    private final Map<Long, Set<String>> sessionsByUser = new HashMap<>();

    /** Returns a transition only when this is the user's first authenticated session. */
    public synchronized Optional<PresenceTransition> connect(String sessionId, Long userId) {
        Long existing = usersBySession.putIfAbsent(sessionId, userId);
        if (existing != null) {
            return Optional.empty();
        }
        Set<String> sessions = sessionsByUser.computeIfAbsent(userId, ignored -> new HashSet<>());
        boolean first = sessions.isEmpty();
        sessions.add(sessionId);
        return first ? Optional.of(new PresenceTransition(userId, true)) : Optional.empty();
    }

    /** Returns a transition only when the user's final authenticated session disappears. */
    public synchronized Optional<PresenceTransition> disconnect(String sessionId) {
        Long userId = usersBySession.remove(sessionId);
        if (userId == null) {
            return Optional.empty();
        }
        Set<String> sessions = sessionsByUser.get(userId);
        if (sessions == null) {
            return Optional.empty();
        }
        sessions.remove(sessionId);
        if (!sessions.isEmpty()) {
            return Optional.empty();
        }
        sessionsByUser.remove(userId);
        return Optional.of(new PresenceTransition(userId, false));
    }

    public synchronized boolean isOnline(Long userId) {
        Set<String> sessions = sessionsByUser.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    public record PresenceTransition(Long userId, boolean online) {
    }
}
