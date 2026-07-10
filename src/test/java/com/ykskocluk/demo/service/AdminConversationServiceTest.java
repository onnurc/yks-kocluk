package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.MessageResponse;
import com.ykskocluk.demo.dto.PageResponse;
import com.ykskocluk.demo.entity.Conversation;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.mapper.MessageMapper;
import com.ykskocluk.demo.repository.ConversationRepository;
import com.ykskocluk.demo.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminConversationServiceTest {

    @Mock
    ConversationRepository conversationRepository;

    @Mock
    MessageRepository messageRepository;

    @Mock
    MessageMapper messageMapper;

    @Mock
    AdminConversationAccessAuditService auditService;

    @InjectMocks
    AdminConversationService adminConversationService;

    @Test
    void getMessages_blankReason_throwsBadRequest() {
        Pageable pageable = PageRequest.of(0, 20);

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> adminConversationService.getMessages(1L, 5L, "   ", pageable));

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getErrorCode()).isEqualTo("ACCESS_REASON_REQUIRED");

        verify(messageRepository, never()).findByConversationId(any(), any());
        verify(auditService, never()).logAccess(any(), any(), any());
    }

    @Test
    void getMessages_conversationNotFound_throwsNotFound() {
        Pageable pageable = PageRequest.of(0, 20);
        when(conversationRepository.existsById(5L)).thenReturn(false);

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> adminConversationService.getMessages(1L, 5L, "Audit Reason", pageable));

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getErrorCode()).isEqualTo("CONVERSATION_NOT_FOUND");

        verify(messageRepository, never()).findByConversationId(any(), any());
        verify(auditService, never()).logAccess(any(), any(), any());
    }

    @Test
    void getMessages_auditFailure_doesNotExposeMessages() {
        Pageable pageable = PageRequest.of(0, 20);
        when(conversationRepository.existsById(5L)).thenReturn(true);
        when(auditService.logAccess(1L, 5L, "Audit Reason"))
                .thenThrow(new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "DATABASE_ERROR", "Log failed"));

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> adminConversationService.getMessages(1L, 5L, "Audit Reason", pageable));

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(ex.getErrorCode()).isEqualTo("DATABASE_ERROR");

        verify(messageRepository, never()).findByConversationId(any(), any());
    }

    @Test
    void getMessages_success_persistsAuditBeforeRetrieval() {
        Pageable pageable = PageRequest.of(0, 20);
        when(conversationRepository.existsById(5L)).thenReturn(true);

        Page<com.ykskocluk.demo.entity.Message> page = new PageImpl<>(List.of());
        when(messageRepository.findByConversationId(eq(5L), any(Pageable.class))).thenReturn(page);

        PageResponse<MessageResponse> response = adminConversationService.getMessages(1L, 5L, "Audit Reason", pageable);

        assertThat(response).isNotNull();

        // Verify inOrder execution
        InOrder inOrder = Mockito.inOrder(auditService, messageRepository);
        inOrder.verify(auditService).logAccess(1L, 5L, "Audit Reason");
        inOrder.verify(messageRepository).findByConversationId(eq(5L), any(Pageable.class));
    }
}
