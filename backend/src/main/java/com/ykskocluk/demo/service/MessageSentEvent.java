package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.MessageResponse;

/**
 * Published inside the send transaction ({@link MessageService#sendMessage}) and consumed
 * AFTER_COMMIT by {@link MessageNotificationListener}. Both send paths — REST
 * ({@code ConversationController}) and STOMP ({@code ChatStompController}) — go through that one
 * method, so this event is the single place message fan-out happens.
 *
 * <p>Like {@link SessionBookedEvent}, it carries a flat snapshot of everything the listener needs:
 * the listener runs after the transaction has closed, where touching a lazy association would
 * throw. {@code message} doubles as the conversation-topic broadcast payload.
 *
 * @param recipientHasAccess whether the recipient can actually open this conversation right now.
 *        False only in the narrow case of a coach writing to a student whose sole subscription is
 *        PENDING_PAYMENT — {@code MessageService.myConversations} hides that thread, so notifying
 *        would badge (or email about) something the recipient cannot open. Evaluated inside the
 *        transaction because it needs a subscription lookup.
 * @param recipientIsStudent picks which debounce column the email claim writes to.
 */
public record MessageSentEvent(
        MessageResponse message,
        Long recipientUserId,
        String recipientEmail,
        boolean recipientIsStudent,
        boolean recipientHasAccess,
        String senderName
) {

    /** Convenience — the conversation this message belongs to. */
    public Long conversationId() {
        return message.conversationId();
    }
}
