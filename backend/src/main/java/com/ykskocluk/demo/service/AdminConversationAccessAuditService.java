package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.AdminConversationAccessResponse;
import com.ykskocluk.demo.entity.AdminConversationAccessLog;
import com.ykskocluk.demo.entity.Conversation;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.AdminConversationAccessLogRepository;
import com.ykskocluk.demo.repository.ConversationRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Service mapping and persisting admin audit trails for conversation access.
 */
@Service
public class AdminConversationAccessAuditService {

    private final AdminConversationAccessLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;

    public AdminConversationAccessAuditService(AdminConversationAccessLogRepository auditLogRepository,
                                               UserRepository userRepository,
                                               ConversationRepository conversationRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
    }

    @Transactional
    public AdminConversationAccessResponse logAccess(Long adminUserId, Long conversationId, String reason) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Yönetici bulunamadı"));

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", "Konuşma bulunamadı"));

        AdminConversationAccessLog log = new AdminConversationAccessLog();
        log.setAdmin(admin);
        log.setConversation(conversation);
        log.setReason(reason);
        log.setAccessedAt(Instant.now());

        auditLogRepository.saveAndFlush(log);

        return new AdminConversationAccessResponse(
                log.getId(),
                log.getAdmin().getId(),
                log.getConversation().getId(),
                log.getReason(),
                log.getAccessedAt()
        );
    }
}
