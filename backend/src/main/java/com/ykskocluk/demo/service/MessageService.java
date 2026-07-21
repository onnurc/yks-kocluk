package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.ConversationResponse;
import com.ykskocluk.demo.dto.MessageResponse;
import com.ykskocluk.demo.dto.PageResponse;
import com.ykskocluk.demo.entity.Conversation;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.Message;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.SubscriptionStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.mapper.ConversationMapper;
import com.ykskocluk.demo.mapper.MessageMapper;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.ConversationRepository;
import com.ykskocluk.demo.repository.MessageRepository;
import com.ykskocluk.demo.repository.SubscriptionRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Messaging core — transport-agnostic so both the REST controller and the Phase 5b
 * WebSocket/STOMP handler go through the same gate + persistence path.
 *
 * <p>Child-safety gate (server-side, never trust the client):
 * <ul>
 *   <li>Opening a conversation requires a non-pending Subscription with the coach.</li>
 *   <li>Every send and read re-checks conversation membership.</li>
 * </ul>
 */
@Service
public class MessageService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final CoachProfileRepository coachProfileRepository;
    private final UserRepository userRepository;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ConsentService consentService;

    public MessageService(ConversationRepository conversationRepository,
                          MessageRepository messageRepository,
                          SubscriptionRepository subscriptionRepository,
                          CoachProfileRepository coachProfileRepository,
                          UserRepository userRepository,
                          ConversationMapper conversationMapper,
                          MessageMapper messageMapper,
                          ConsentService consentService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.coachProfileRepository = coachProfileRepository;
        this.userRepository = userRepository;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.consentService = consentService;
    }

    /** Student opens (or re-fetches) the conversation with a coach. Gate enforced here. */
    @Transactional
    public ConversationResponse openConversation(Long studentUserId, Long coachProfileId) {
        CoachProfile coach = coachProfileRepository.findById(coachProfileId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COACH_NOT_FOUND", "Koç bulunamadı"));

        User student = userRepository.findById(studentUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Kullanıcı bulunamadı"));
        consentService.checkConsentRequiredForAction(student);

        // The gate: must have history access with this coach.
        if (!subscriptionRepository.existsHistoryAccessSubscription(studentUserId, coachProfileId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "MESSAGING_NOT_ALLOWED",
                    "Yalnızca abone olduğunuz koçlarla mesajlaşabilirsiniz");
        }

        Conversation conversation = conversationRepository
                .findByStudentIdAndCoachProfileId(studentUserId, coachProfileId)
                .orElseGet(() -> {
                    Conversation c = new Conversation();
                    c.setStudent(student);
                    c.setCoachProfile(coach);
                    c.setLastMessageAt(Instant.now());
                    return conversationRepository.save(c);
                });
        return toResponse(conversation, studentUserId);
    }

    /** Sends a message. Sender must be a participant. Bumps last_message_at in the same tx. */
    @Transactional
    public MessageResponse sendMessage(Long senderUserId, Long conversationId, String content) {
        Conversation conversation = requireParticipant(conversationId, senderUserId);

        // If the sender is the student, verify they still have an ACTIVE subscription
        if (conversation.getStudent().getId().equals(senderUserId)) {
            if (!subscriptionRepository.existsByStudentIdAndCoachProfileIdAndStatus(senderUserId, conversation.getCoachProfile().getId(), SubscriptionStatus.ACTIVE)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "MESSAGING_NOT_ALLOWED",
                        "Mesaj göndermek için aktif bir aboneliğiniz olmalıdır");
            }
        }

        User sender = userRepository.findById(senderUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Kullanıcı bulunamadı"));

        consentService.checkConsentRequiredForAction(sender);

        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(content);
        // Flush so @CreatedDate (sent time) is populated, then mirror it onto the conversation.
        Message saved = messageRepository.saveAndFlush(message);
        conversation.setLastMessageAt(saved.getCreatedAt()); // Decision #3: same tx as the insert

        return messageMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> myConversations(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Kullanıcı bulunamadı"));

        List<Conversation> list = conversationRepository.findForUser(userId);
        if (user.getRole() == com.ykskocluk.demo.enums.Role.STUDENT) {
            list = list.stream()
                    .filter(conversation -> subscriptionRepository.existsHistoryAccessSubscription(userId, conversation.getCoachProfile().getId()))
                    .toList();
        }
        return list.stream()
                .map(c -> toResponse(c, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<MessageResponse> history(Long userId, Long conversationId, Pageable pageable) {
        Conversation conversation = requireParticipant(conversationId, userId);

        // If the reader is the student, verify they still have history access
        if (conversation.getStudent().getId().equals(userId)) {
            if (!subscriptionRepository.existsHistoryAccessSubscription(userId, conversation.getCoachProfile().getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "MESSAGING_NOT_ALLOWED",
                        "Mesaj geçmişini görüntülemek için aktif bir aboneliğiniz olmalıdır");
            }
        }

        return PageResponse.from(
                messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable)
                        .map(messageMapper::toResponse));
    }

    /** Marks the other party's messages as read. Returns how many were flipped. */
    @Transactional
    public int markRead(Long userId, Long conversationId) {
        requireParticipant(conversationId, userId);
        return messageRepository.markRead(conversationId, userId, Instant.now());
    }

    /** Read-only membership check for the STOMP SUBSCRIBE interceptor (5b). */
    @Transactional(readOnly = true)
    public boolean isParticipant(Long userId, Long conversationId) {
        return conversationRepository.findById(conversationId)
                .map(c -> {
                    if (c.getStudent().getId().equals(userId)) {
                        return subscriptionRepository.existsHistoryAccessSubscription(userId, c.getCoachProfile().getId());
                    }
                    return c.getCoachProfile().getUser().getId().equals(userId);
                })
                .orElse(false);
    }

    // --- helpers ---

    private Conversation requireParticipant(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND",
                        "Konuşma bulunamadı"));
        boolean participant = conversation.getStudent().getId().equals(userId)
                || conversation.getCoachProfile().getUser().getId().equals(userId);
        if (!participant) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_CONVERSATION_PARTICIPANT",
                    "Bu konuşmaya erişiminiz yok");
        }
        return conversation;
    }

    private ConversationResponse toResponse(Conversation conversation, Long viewerUserId) {
        long unread = messageRepository.countByConversationIdAndSenderIdNotAndReadAtIsNull(
                conversation.getId(), viewerUserId);
        return conversationMapper.toResponse(conversation, unread);
    }
}
