package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.service.MessageService;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP send handler (Phase 5b). Delegates to the SAME {@link MessageService#sendMessage}
 * the REST path uses — so the child-safety gate and conversation-membership re-check apply
 * identically. A failed gate/membership check throws, which becomes a STOMP ERROR frame.
 *
 * <p>This handler deliberately does <strong>not</strong> broadcast. Fan-out to the conversation
 * topic (plus the recipient's badge and email) is owned by
 * {@code MessageNotificationListener} after the send transaction commits, so REST-sent messages —
 * the client's fallback whenever the socket is down — reach subscribers too. Broadcasting here as
 * well would deliver every socket-sent message twice.
 */
@Controller
public class ChatStompController {

    private final MessageService messageService;

    public ChatStompController(MessageService messageService) {
        this.messageService = messageService;
    }

    @MessageMapping("/conversations/{id}/send")
    public void send(@DestinationVariable Long id, @Payload String content, Principal principal) {
        messageService.sendMessage(currentUserId(principal), id, content);
    }

    private Long currentUserId(Principal principal) {
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        throw new MessagingException("Kimlik doğrulanamadı");
    }
}
