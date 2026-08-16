package com.ykskocluk.demo.service;

import com.ykskocluk.demo.config.MessageNotificationProperties;
import com.ykskocluk.demo.dto.UserNotificationResponse;
import com.ykskocluk.demo.integration.MailClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

/**
 * The single fan-out point for a sent chat message — one of the sanctioned uses of
 * {@code @TransactionalEventListener(AFTER_COMMIT)} (CLAUDE.md).
 *
 * <p>Three deliveries, in order of decreasing immediacy:
 * <ol>
 *   <li><b>Conversation topic</b> — whoever has the thread open sees the message appear.</li>
 *   <li><b>User queue</b> — the recipient's nav badge and inbox update from any page.</li>
 *   <li><b>Email</b> — only if the recipient isn't connected at all (see
 *       {@link #shouldSendEmail}).</li>
 * </ol>
 *
 * <p><b>Why after commit rather than in the send path.</b> It used to live in
 * {@code ChatStompController}, which meant a REST-sent message (the client's fallback when the
 * socket is down) reached nobody until a page reload. Moving it here covers both send paths and
 * guarantees nothing is broadcast for a row that then rolls back.
 *
 * <p>Each step is isolated: the message is already committed, so a broker or Resend failure is
 * logged and never propagated — and a failed broadcast must not cost the recipient their email.
 */
@Component
public class MessageNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(MessageNotificationListener.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry userRegistry;
    private final MessageService messageService;
    private final MailClient mailClient;
    private final MessageNotificationProperties properties;
    private final ObjectMapper objectMapper;

    public MessageNotificationListener(SimpMessagingTemplate messagingTemplate,
                                       SimpUserRegistry userRegistry,
                                       MessageService messageService,
                                       MailClient mailClient,
                                       MessageNotificationProperties properties,
                                       ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.userRegistry = userRegistry;
        this.messageService = messageService;
        this.mailClient = mailClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(MessageSentEvent event) {
        broadcastToConversation(event);
        pushToRecipient(event);
        emailIfUnreachable(event, Instant.now());
    }

    /** Live message for anyone currently viewing the thread (sender's echo included). */
    private void broadcastToConversation(MessageSentEvent event) {
        try {
            messagingTemplate.convertAndSend("/topic/conversations/" + event.conversationId(),
                    objectMapper.writeValueAsString(event.message()));
        } catch (Exception e) {
            log.error("Conversation broadcast failed for message {}: {}",
                    event.message().id(), e.getMessage(), e);
        }
    }

    /**
     * Badge/inbox signal on the recipient's own queue. Skipped when the recipient can't open the
     * thread — see {@code MessageSentEvent.recipientHasAccess}.
     */
    private void pushToRecipient(MessageSentEvent event) {
        if (!event.recipientHasAccess()) {
            return;
        }
        try {
            UserNotificationResponse payload = UserNotificationResponse.newMessage(
                    event.conversationId(), messageService.unreadTotal(event.recipientUserId()));
            messagingTemplate.convertAndSendToUser(
                    StompUserDestinations.userName(event.recipientUserId()),
                    StompUserDestinations.NOTIFICATIONS,
                    objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.error("Notification push failed for user {}: {}",
                    event.recipientUserId(), e.getMessage(), e);
        }
    }

    private void emailIfUnreachable(MessageSentEvent event, Instant now) {
        try {
            if (!shouldSendEmail(event, now)) {
                return;
            }
            mailClient.sendNewMessageNotification(event.recipientEmail(), event.senderName(),
                    properties.conversationLink(event.conversationId()));
        } catch (Exception e) {
            // The message is committed and already delivered in-app; a mail failure is not the
            // sender's problem. Note the claim is NOT rolled back — see shouldSendEmail.
            log.error("New-message email failed for user {}: {}",
                    event.recipientUserId(), e.getMessage(), e);
        }
    }

    /**
     * <strong>The one place that decides whether a new-message email is sent.</strong> Every
     * condition lives here on purpose: adding a user-facing "email me about new messages" toggle
     * (deliberately deferred — not a legal requirement, these are transactional notifications, and
     * the debounce below is what actually keeps the volume sane) is then a single extra clause in
     * this method and nothing else in the codebase moves.
     *
     * <p>In order:
     * <ol>
     *   <li><b>Access</b> — never email about a thread the recipient can't open.</li>
     *   <li><b>Presence</b> — a connected user has the app open and gets the badge, so email would
     *       be pure noise. {@link SimpUserRegistry} is fed by the in-memory simple broker, so this
     *       answer is correct for <em>this</em> instance only: running more than one container
     *       would make users connected elsewhere look offline and re-introduce some noise (the
     *       debounce still caps it). Single persistent container is a standing decision
     *       (CLAUDE.md), so that trade is accepted rather than solved with a shared registry.
     *       Note "connected" is not "looking" — a backgrounded tab counts as reachable, and
     *       chasing real attention (focus/idle tracking) is deliberately out of scope.</li>
     *   <li><b>Debounce</b> — an atomic claim on the recipient's column of the conversation, so a
     *       burst of ten messages yields one email, and two concurrent sends can't both win.
     *       Claimed last because it is the only step with a side effect: if the mail then fails,
     *       the window is already burned and the recipient waits it out rather than being emailed
     *       twice. That is the same trade {@code SessionReminderJob} makes.</li>
     * </ol>
     */
    private boolean shouldSendEmail(MessageSentEvent event, Instant now) {
        if (!event.recipientHasAccess()) {
            return false;
        }
        if (isConnected(event.recipientUserId())) {
            return false;
        }
        return messageService.claimEmailNotification(event.conversationId(),
                event.recipientIsStudent(), now, now.minus(properties.debounce()));
    }

    /** True if the recipient has at least one live STOMP session on this instance. */
    private boolean isConnected(Long userId) {
        return userRegistry.getUser(StompUserDestinations.userName(userId)) != null;
    }
}
