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
import com.ykskocluk.demo.security.ratelimit.AuthenticatedActionRateLimitService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final AccountReadinessService accountReadinessService;
    private final ApplicationEventPublisher eventPublisher;
    private final ChatPresenceService presenceService;
    private final AuthenticatedActionRateLimitService actionRateLimit;

    public MessageService(ConversationRepository conversationRepository,
                          MessageRepository messageRepository,
                          SubscriptionRepository subscriptionRepository,
                          CoachProfileRepository coachProfileRepository,
                          UserRepository userRepository,
                          ConversationMapper conversationMapper,
                          MessageMapper messageMapper,
                          AccountReadinessService accountReadinessService,
                          ApplicationEventPublisher eventPublisher,
                          ChatPresenceService presenceService,
                          AuthenticatedActionRateLimitService actionRateLimit) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.coachProfileRepository = coachProfileRepository;
        this.userRepository = userRepository;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.accountReadinessService = accountReadinessService;
        this.eventPublisher = eventPublisher;
        this.presenceService = presenceService;
        this.actionRateLimit = actionRateLimit;
    }

    /** Student opens (or re-fetches) the conversation with a coach. Gate enforced here. */
    @Transactional
    public ConversationResponse openConversation(Long studentUserId, Long coachProfileId) {
        User student = userRepository.findById(studentUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Kullanıcı bulunamadı"));
        accountReadinessService.requireReady(student);

        CoachProfile coach = coachProfileRepository.findById(coachProfileId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COACH_NOT_FOUND", "Koç bulunamadı"));

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
        return toResponse(conversation, studentUserId,
                messageRepository.findFirstByConversationIdOrderByCreatedAtDesc(conversation.getId()).orElse(null));
    }

    /** Sends a message. Sender must be a participant. Bumps last_message_at in the same tx. */
    @Transactional
    public MessageResponse sendMessage(Long senderUserId, Long conversationId, String content) {
        validateMessageContent(content);
        actionRateLimit.checkMessageSend(senderUserId);
        User sender = userRepository.findById(senderUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Kullanıcı bulunamadı"));
        accountReadinessService.requireReady(sender);

        Conversation conversation = requireParticipant(conversationId, senderUserId);

        // If the sender is the student, verify they still have an ACTIVE subscription
        if (conversation.getStudent().getId().equals(senderUserId)) {
            if (!subscriptionRepository.existsByStudentIdAndCoachProfileIdAndStatus(senderUserId, conversation.getCoachProfile().getId(), SubscriptionStatus.ACTIVE)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "MESSAGING_NOT_ALLOWED",
                        "Mesaj göndermek için aktif bir aboneliğiniz olmalıdır");
            }
        }

        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(content);
        // Flush so @CreatedDate (sent time) is populated, then mirror it onto the conversation.
        Message saved = messageRepository.saveAndFlush(message);
        conversation.setLastMessageAt(saved.getCreatedAt()); // Decision #3: same tx as the insert

        MessageResponse response = messageMapper.toResponse(saved);
        // Fan-out (topic broadcast + badge push + email) happens AFTER_COMMIT in
        // MessageNotificationListener, for both this and the REST send path. Built here, inside
        // the transaction, because the listener runs with the entities already detached.
        eventPublisher.publishEvent(buildSentEvent(conversation, sender, response));
        return response;
    }

    private void validateMessageContent(String content) {
        if (content == null || content.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MESSAGE_BLANK", "Mesaj boş olamaz");
        }
        if (content.length() > 4000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MESSAGE_TOO_LONG",
                    "Mesaj en fazla 4000 karakter olabilir");
        }
    }

    /**
     * Snapshots what the after-commit listener needs. The recipient is simply "the participant who
     * isn't the sender"; a student recipient's access is re-checked here because the inbox hides
     * threads without history access, and notifying about a hidden thread would badge something
     * the user cannot open.
     */
    private MessageSentEvent buildSentEvent(Conversation conversation, User sender,
                                            MessageResponse response) {
        User student = conversation.getStudent();
        User coach = conversation.getCoachProfile().getUser();
        boolean senderIsStudent = student.getId().equals(sender.getId());
        User recipient = senderIsStudent ? coach : student;
        boolean recipientHasAccess = !senderIsStudent
                ? subscriptionRepository.existsHistoryAccessSubscription(
                        student.getId(), conversation.getCoachProfile().getId())
                : true; // the coach side is never subscription-gated
        return new MessageSentEvent(response, recipient.getId(), recipient.getEmail(),
                !senderIsStudent, recipientHasAccess, sender.getFullName());
    }

    /**
     * Authoritative unread total for the nav badge, pushed on every notification.
     *
     * <p>REQUIRES_NEW for the same reason as {@link #claimEmailNotification} — see there.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public long unreadTotal(Long userId) {
        return messageRepository.countUnreadForUser(userId);
    }

    /**
     * Claims the right to email {@code recipient} about this conversation, returning false if a
     * mail already went out inside the debounce window (or a concurrent send just won the claim).
     * Committed before the mail is dispatched — a crash mid-send costs one notification rather
     * than risking a duplicate, the same trade {@code SessionService.claimReminder} makes.
     *
     * <p><strong>REQUIRES_NEW, not the default.</strong> The caller is an AFTER_COMMIT listener,
     * and during that phase the original transaction is still bound to the thread even though it
     * has already committed — so a plain {@code @Transactional} would silently join a dead
     * transaction and the UPDATE fails with "No active transaction". {@code claimReminder} gets
     * away with the default only because its caller is a scheduled job with no ambient
     * transaction. A fresh one is needed here.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimEmailNotification(Long conversationId, boolean recipientIsStudent,
                                          Instant now, Instant threshold) {
        int claimed = recipientIsStudent
                ? conversationRepository.claimStudentNotification(conversationId, now, threshold)
                : conversationRepository.claimCoachNotification(conversationId, now, threshold);
        return claimed > 0;
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
        List<Long> conversationIds = list.stream().map(Conversation::getId).toList();
        Map<Long, Message> latestByConversation = conversationIds.isEmpty()
                ? Map.of()
                : messageRepository.findLatestByConversationIds(conversationIds).stream()
                        .collect(Collectors.toMap(message -> message.getConversation().getId(),
                                Function.identity(), (newest, ignored) -> newest, LinkedHashMap::new));

        return list.stream()
                .map(c -> toResponse(c, userId, latestByConversation.get(c.getId())))
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

    private ConversationResponse toResponse(Conversation conversation, Long viewerUserId,
                                            Message lastMessage) {
        long unread = messageRepository.countByConversationIdAndSenderIdNotAndReadAtIsNull(
                conversation.getId(), viewerUserId);
        Long studentId = conversation.getStudent().getId();
        Long counterpartUserId = studentId.equals(viewerUserId)
                ? conversation.getCoachProfile().getUser().getId()
                : studentId;
        return conversationMapper.toResponse(conversation, unread,
                lastMessage == null ? null : lastMessage.getContent(),
                lastMessage == null ? null : lastMessage.getCreatedAt(),
                counterpartUserId, presenceService.isOnline(counterpartUserId));
    }
}
