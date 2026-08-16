package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.MessageResponse;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.service.MessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatStompControllerTest {
    @Mock MessageService messageService;

    /**
     * Both participants send through the one gated service method. The controller used to
     * broadcast here as well; fan-out now belongs to MessageNotificationListener (after commit,
     * covering the REST path too), which is what keeps socket-sent messages from being delivered
     * twice — asserted end to end in {@code MessageNotificationIntegrationTest}.
     */
    @Test
    void studentAndCoachMessagesBothGoThroughTheSharedGatedSendPath() {
        when(messageService.sendMessage(10L, 50L, "student")).thenReturn(response(1L, 10L, "student"));
        when(messageService.sendMessage(20L, 50L, "coach")).thenReturn(response(2L, 20L, "coach"));
        ChatStompController controller = new ChatStompController(messageService);

        controller.send(50L, "student", principal(10L, "STUDENT"));
        controller.send(50L, "coach", principal(20L, "COACH"));

        verify(messageService).sendMessage(10L, 50L, "student");
        verify(messageService).sendMessage(20L, 50L, "coach");
        verifyNoMoreInteractions(messageService);
    }

    @Test
    void adminSendRemainsRejectedByMessageServiceDefenseInDepth() {
        ChatStompController controller = new ChatStompController(messageService);
        when(messageService.sendMessage(30L, 50L, "forbidden"))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "NOT_CONVERSATION_PARTICIPANT", "forbidden"));

        assertThatThrownBy(() -> controller.send(50L, "forbidden", principal(30L, "ADMIN")))
                .isInstanceOf(ApiException.class);
    }

    private UsernamePasswordAuthenticationToken principal(Long id, String role) {
        return new UsernamePasswordAuthenticationToken(id, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private MessageResponse response(Long id, Long senderId, String content) {
        return new MessageResponse(id, 50L, senderId, "Sender", content, Instant.now(), null);
    }
}
