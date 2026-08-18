package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.PresenceResponse;
import com.ykskocluk.demo.repository.ConversationRepository;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import tools.jackson.databind.ObjectMapper;

/** Tracks only successfully authenticated participant STOMP sessions and broadcasts transitions. */
@Component
public class ChatPresenceEventListener {

    private final ChatPresenceService presenceService;
    private final ConversationRepository conversationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public ChatPresenceEventListener(ChatPresenceService presenceService,
                                     ConversationRepository conversationRepository,
                                     SimpMessagingTemplate messagingTemplate,
                                     ObjectMapper objectMapper) {
        this.presenceService = presenceService;
        this.conversationRepository = conversationRepository;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void connected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        if (!(event.getUser() instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof Long userId)
                || !isParticipant(authentication)
                || accessor.getSessionId() == null) {
            return;
        }
        presenceService.connect(accessor.getSessionId(), userId).ifPresent(this::broadcast);
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        presenceService.disconnect(event.getSessionId()).ifPresent(this::broadcast);
    }

    private boolean isParticipant(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(authority ->
                "ROLE_STUDENT".equals(authority.getAuthority())
                        || "ROLE_COACH".equals(authority.getAuthority()));
    }

    private void broadcast(ChatPresenceService.PresenceTransition transition) {
        String payload = objectMapper.writeValueAsString(
                new PresenceResponse(transition.userId(), transition.online()));
        for (Long conversationId : conversationRepository.findIdsForUser(transition.userId())) {
            messagingTemplate.convertAndSend(
                    "/topic/conversations/" + conversationId + "/presence", payload);
        }
    }
}
