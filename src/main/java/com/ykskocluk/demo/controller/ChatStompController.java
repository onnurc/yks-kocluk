package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.MessageResponse;
import com.ykskocluk.demo.service.MessageService;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import tools.jackson.databind.ObjectMapper;

import java.security.Principal;

/**
 * STOMP send handler (Phase 5b). Delegates to the SAME {@link MessageService#sendMessage}
 * the REST path uses — so the child-safety gate and conversation-membership re-check apply
 * identically. The persisted message is then broadcast to the conversation topic. A failed
 * gate/membership check throws, which becomes a STOMP ERROR frame (nothing is broadcast).
 */
@Controller
public class ChatStompController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public ChatStompController(MessageService messageService,
                               SimpMessagingTemplate messagingTemplate,
                               ObjectMapper objectMapper) {
        this.messageService = messageService;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @MessageMapping("/conversations/{id}/send")
    public void send(@DestinationVariable Long id, @Payload String content, Principal principal) {
        MessageResponse response = messageService.sendMessage(currentUserId(principal), id, content);
        messagingTemplate.convertAndSend("/topic/conversations/" + id, objectMapper.writeValueAsString(response));
    }

    private Long currentUserId(Principal principal) {
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        throw new MessagingException("Kimlik doğrulanamadı");
    }
}
