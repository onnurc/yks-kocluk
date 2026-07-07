package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.AdminConversationAccessResponse;
import com.ykskocluk.demo.entity.AdminConversationAccessLog;
import com.ykskocluk.demo.entity.Conversation;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.AdminConversationAccessLogRepository;
import com.ykskocluk.demo.repository.ConversationRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AdminConversationAccessAuditService}.
 */
@ExtendWith(MockitoExtension.class)
class AdminConversationAccessAuditServiceTest {

    @Mock
    AdminConversationAccessLogRepository auditLogRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    ConversationRepository conversationRepository;

    @InjectMocks
    AdminConversationAccessAuditService auditService;

    @Test
    void logAccess_success_savesAuditLog() {
        User admin = new User();
        ReflectionTestUtils.setField(admin, "id", 1L);
        Conversation conversation = new Conversation();
        ReflectionTestUtils.setField(conversation, "id", 5L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(conversationRepository.findById(5L)).thenReturn(Optional.of(conversation));

        AdminConversationAccessResponse response = auditService.logAccess(1L, 5L, "Security check");

        assertThat(response).isNotNull();
        assertThat(response.conversationId()).isEqualTo(5L);
        assertThat(response.reason()).isEqualTo("Security check");
        assertThat(response.accessedAt()).isNotNull();

        verify(auditLogRepository).saveAndFlush(argThat(log ->
                log.getAdmin() == admin &&
                log.getConversation() == conversation &&
                log.getReason().equals("Security check") &&
                log.getAccessedAt() != null
        ));
    }

    @Test
    void logAccess_adminNotFound_throwsNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> auditService.logAccess(1L, 5L, "Security check"));

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getErrorCode()).isEqualTo("USER_NOT_FOUND");

        verify(auditLogRepository, never()).saveAndFlush(any());
    }

    @Test
    void logAccess_conversationNotFound_throwsNotFound() {
        User admin = new User();
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(conversationRepository.findById(5L)).thenReturn(Optional.empty());

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> auditService.logAccess(1L, 5L, "Security check"));

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getErrorCode()).isEqualTo("CONVERSATION_NOT_FOUND");

        verify(auditLogRepository, never()).saveAndFlush(any());
    }
}
