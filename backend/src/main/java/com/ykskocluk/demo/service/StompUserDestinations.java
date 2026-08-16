package com.ykskocluk.demo.service;

/**
 * The user-scoped STOMP destination and the name Spring resolves it against.
 *
 * <p><strong>Why this exists.</strong> {@code SimpMessagingTemplate.convertAndSendToUser(user, ...)}
 * and {@code SimpUserRegistry.getUser(user)} both key off {@code Principal.getName()}. The principal
 * bound at CONNECT ({@code StompAuthChannelInterceptor}) is a
 * {@code UsernamePasswordAuthenticationToken} whose principal is a raw {@code Long} user id, so
 * {@code AbstractAuthenticationToken.getName()} finds neither a {@code UserDetails} nor a
 * {@code Principal} and falls through to {@code principal.toString()} — i.e. the id as a string.
 * That works, but it is an implicit contract: change the principal type and every notification
 * silently stops being delivered, with no error anywhere.
 *
 * <p>Routing both the send side and the presence check through {@link #userName(Long)} keeps that
 * assumption in one place, and {@code StompAuthChannelInterceptorTest} asserts it directly.
 */
public final class StompUserDestinations {

    /** Client subscribes to {@code /user/queue/notifications}; the broker resolves the prefix. */
    public static final String NOTIFICATIONS = "/queue/notifications";

    private StompUserDestinations() {
    }

    /** The {@code Principal.getName()} the STOMP layer knows this user by. */
    public static String userName(Long userId) {
        return String.valueOf(userId);
    }
}
