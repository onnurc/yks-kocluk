package com.ykskocluk.demo.dto;

/**
 * Payload pushed to {@code /user/queue/notifications} — the user-scoped channel the app layout
 * listens on from every page (the conversation topic only reaches whoever has that conversation
 * open).
 *
 * <p>{@code unreadTotal} is computed server-side on every push so the nav badge is authoritative:
 * a client that incremented its own counter would drift across multiple tabs, reconnects and
 * reads-elsewhere. Deliberately carries no message text — the recipient may not even be allowed
 * to open the thread by the time this lands, and content belongs on the conversation topic.
 *
 * @param type discriminator so the channel can carry other notification kinds later without a
 *        second subscription.
 */
public record UserNotificationResponse(
        String type,
        Long conversationId,
        long unreadTotal
) {

    public static final String NEW_MESSAGE = "NEW_MESSAGE";

    public static UserNotificationResponse newMessage(Long conversationId, long unreadTotal) {
        return new UserNotificationResponse(NEW_MESSAGE, conversationId, unreadTotal);
    }
}
