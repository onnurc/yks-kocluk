package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.MessageResponse;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.service.MessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatStompControllerTest {
    @Mock MessageService messageService;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock ObjectMapper objectMapper;

    @Test
    void studentAndCoachMessagesUseSameFanOutTopicForParticipantsAndAdminObserver() {
        ChatStompController controller = new ChatStompController(messageService, messagingTemplate, objectMapper);
        MessageResponse studentMessage = response(1L, 10L, "student");
        MessageResponse coachMessage = response(2L, 20L, "coach");
        when(messageService.sendMessage(10L, 50L, "student")).thenReturn(studentMessage);
        when(messageService.sendMessage(20L, 50L, "coach")).thenReturn(coachMessage);
        when(objectMapper.writeValueAsString(studentMessage)).thenReturn("student-json");
        when(objectMapper.writeValueAsString(coachMessage)).thenReturn("coach-json");

        controller.send(50L, "student", principal(10L, "STUDENT"));
        controller.send(50L, "coach", principal(20L, "COACH"));

        verify(messagingTemplate).convertAndSend("/topic/conversations/50", "student-json");
        verify(messagingTemplate).convertAndSend("/topic/conversations/50", "coach-json");
    }

    @Test
    void adminSendRemainsRejectedByMessageServiceDefenseInDepth() {
        ChatStompController controller = new ChatStompController(messageService, messagingTemplate, objectMapper);
        when(messageService.sendMessage(30L, 50L, "forbidden"))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "NOT_CONVERSATION_PARTICIPANT", "forbidden"));

        assertThatThrownBy(() -> controller.send(50L, "forbidden", principal(30L, "ADMIN")))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(messagingTemplate);
    }

    private UsernamePasswordAuthenticationToken principal(Long id, String role) {
        return new UsernamePasswordAuthenticationToken(id, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private MessageResponse response(Long id, Long senderId, String content) {
        return new MessageResponse(id, 50L, senderId, "Sender", content, Instant.now(), null);
    }
}
