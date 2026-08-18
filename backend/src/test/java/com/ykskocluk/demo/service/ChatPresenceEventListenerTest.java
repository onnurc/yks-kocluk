package com.ykskocluk.demo.service;

import com.ykskocluk.demo.repository.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatPresenceEventListenerTest {

    @Mock ChatPresenceService presenceService;
    @Mock ConversationRepository conversationRepository;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock SessionConnectedEvent connectedEvent;
    @Mock SessionDisconnectEvent disconnectEvent;

    ChatPresenceEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new ChatPresenceEventListener(presenceService, conversationRepository,
                messagingTemplate, JsonMapper.builder().build());
    }

    @Test
    void authenticatedParticipantConnectionRegistersAndBroadcastsOnline() {
        when(connectedEvent.getMessage()).thenReturn(connectedMessage("session-a"));
        when(connectedEvent.getUser()).thenReturn(authentication(7L, "ROLE_STUDENT"));
        when(presenceService.connect("session-a", 7L)).thenReturn(Optional.of(
                new ChatPresenceService.PresenceTransition(7L, true)));
        when(conversationRepository.findIdsForUser(7L)).thenReturn(List.of(12L));

        listener.connected(connectedEvent);

        verify(presenceService).connect("session-a", 7L);
        verify(messagingTemplate).convertAndSend(
                "/topic/conversations/12/presence", "{\"userId\":7,\"online\":true}");
    }

    @Test
    void unauthenticatedOrAdminConnectionNeverRegistersPresence() {
        when(connectedEvent.getMessage()).thenReturn(connectedMessage("session-a"));
        listener.connected(connectedEvent);
        verifyNoInteractions(presenceService, conversationRepository, messagingTemplate);

        when(connectedEvent.getMessage()).thenReturn(connectedMessage("session-admin"));
        when(connectedEvent.getUser()).thenReturn(authentication(9L, "ROLE_ADMIN"));
        listener.connected(connectedEvent);
        verify(presenceService, never()).connect("session-admin", 9L);
    }

    @Test
    void finalDisconnectBroadcastsOffline() {
        when(disconnectEvent.getSessionId()).thenReturn("session-a");
        when(presenceService.disconnect("session-a")).thenReturn(Optional.of(
                new ChatPresenceService.PresenceTransition(7L, false)));
        when(conversationRepository.findIdsForUser(7L)).thenReturn(List.of(12L));

        listener.disconnected(disconnectEvent);

        verify(messagingTemplate).convertAndSend(
                "/topic/conversations/12/presence", "{\"userId\":7,\"online\":false}");
    }

    private Message<byte[]> connectedMessage(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECTED);
        accessor.setSessionId(sessionId);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private UsernamePasswordAuthenticationToken authentication(Long userId, String authority) {
        return new UsernamePasswordAuthenticationToken(userId, null,
                List.of(new SimpleGrantedAuthority(authority)));
    }
}
