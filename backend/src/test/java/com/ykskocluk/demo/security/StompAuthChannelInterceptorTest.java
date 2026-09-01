package com.ykskocluk.demo.security;

import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.service.MessageService;
import com.ykskocluk.demo.service.AdminConversationService;
import com.ykskocluk.demo.service.StompUserDestinations;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.impl.DefaultClaims;
import io.jsonwebtoken.impl.DefaultJws;
import io.jsonwebtoken.impl.DefaultJwsHeader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link StompAuthChannelInterceptor} ensuring suspended users are blocked from connecting.
 */
@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    @Mock
    JwtService jwtService;

    @Mock
    MessageService messageService;

    @Mock
    UserRepository userRepository;

    @Mock
    AdminConversationService adminConversationService;

    @Mock
    WebSocketSessionRegistry sessionRegistry;

    StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthChannelInterceptor(jwtService, messageService, userRepository,
                adminConversationService, sessionRegistry);
        lenient().when(sessionRegistry.bindAuthenticatedSession(anyString(), anyLong())).thenReturn(true);
        lenient().when(sessionRegistry.addSubscription(anyString(), anyString())).thenReturn(true);
    }

    @Test
    void preSend_connectSuspendedUser_throwsMessagingException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.addNativeHeader("Authorization", "Bearer suspended-token");
        Message<byte[]> message = message(accessor);

        Claims claims = new DefaultClaims(Map.of("sub", "2", "role", "STUDENT"));
        Jws jws = mock(Jws.class);
        when(jws.getPayload()).thenReturn(claims);
        when(jwtService.parse("suspended-token")).thenReturn(jws);

        User suspendedUser = new User();
        suspendedUser.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(suspendedUser));

        MessagingException ex = catchThrowableOfType(MessagingException.class,
                () -> interceptor.preSend(message, null));

        assertThat(ex).isNotNull();
        assertThat(ex.getMessage()).contains("Hesabınız askıya alınmıştır");
    }

    @Test
    void preSend_connectDeletedUser_throwsMessagingException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.addNativeHeader("Authorization", "Bearer deleted-token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Claims claims = new DefaultClaims(Map.of("sub", "2", "role", "STUDENT"));
        Jws jws = mock(Jws.class);
        when(jws.getPayload()).thenReturn(claims);
        when(jwtService.parse("deleted-token")).thenReturn(jws);

        User deletedUser = new User();
        deletedUser.setStatus(UserStatus.DELETED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(deletedUser));

        MessagingException ex = catchThrowableOfType(MessagingException.class,
                () -> interceptor.preSend(message, null));

        assertThat(ex).isNotNull();
        assertThat(ex.getMessage()).contains("Hesap artık kullanılamaz");
    }

    @Test
    void preSend_connectActiveUser_setsAuthentication() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.setSessionId("session-1");
        accessor.addNativeHeader("Authorization", "Bearer active-token");
        Message<byte[]> message = message(accessor);

        Claims claims = validClaims(3L, 0);
        Jws jws = mock(Jws.class);
        when(jws.getPayload()).thenReturn(claims);
        when(jwtService.parse("active-token")).thenReturn(jws);

        User activeUser = new User();
        activeUser.setStatus(UserStatus.ACTIVE);
        activeUser.setRole(Role.STUDENT);
        when(userRepository.findById(3L)).thenReturn(Optional.of(activeUser));

        Message<?> result = interceptor.preSend(message, null);
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);

        Authentication auth = (Authentication) resultAccessor.getUser();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(3L);
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_STUDENT");
        // The user-scoped notification channel is addressed by Principal.getName(), which for this
        // token type falls through to principal.toString(). Nothing else enforces that, and if it
        // ever stops matching the user id, convertAndSendToUser silently delivers to nobody — so
        // it is pinned here, against the shared helper both the send and presence sides use.
        assertThat(auth.getName()).isEqualTo(StompUserDestinations.userName(3L));
    }

    @Test
    void preSend_subscribeToRawBrokerQueue_throwsMessagingException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        // What Spring resolves "/user/queue/notifications" to internally. Reaching it directly
        // would mean reading a channel addressed to someone else's session.
        accessor.setDestination("/queue/notifications-userabc123");
        accessor.setUser(auth(3L, "ROLE_STUDENT"));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessagingException.class);
        verifyNoInteractions(messageService);
    }

    @Test
    void preSend_connectMissingUser_rejectsSignedToken() {
        StompHeaderAccessor accessor = connect("orphan-token");
        Claims claims = new DefaultClaims(Map.of("sub", "99", "role", "ADMIN"));
        Jws jws = mock(Jws.class);
        when(jws.getPayload()).thenReturn(claims);
        when(jwtService.parse("orphan-token")).thenReturn(jws);
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(catchThrowableOfType(MessagingException.class,
                () -> interceptor.preSend(message(accessor), null))).isNotNull();
    }

    @Test
    void preSend_connectPasswordVersionMismatch_rejectsToken() {
        StompHeaderAccessor accessor = connect("stale-token");
        Claims claims = new DefaultClaims(Map.of(
                "sub", "3", "role", "STUDENT", "passwordVersion", 0));
        Jws jws = mock(Jws.class);
        when(jws.getPayload()).thenReturn(claims);
        when(jwtService.parse("stale-token")).thenReturn(jws);
        User user = new User(); user.setStatus(UserStatus.ACTIVE); user.setRole(Role.STUDENT);
        user.setPasswordVersion(1);
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));

        MessagingException error = catchThrowableOfType(MessagingException.class,
                () -> interceptor.preSend(message(accessor), null));
        assertThat(error.getMessage()).contains("artık geçerli değil");
    }

    private StompHeaderAccessor connect(String token) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.setSessionId("session-1");
        accessor.addNativeHeader("Authorization", "Bearer " + token);
        return accessor;
    }

    private Message<byte[]> message(StompHeaderAccessor accessor) {
        if (accessor.getSessionId() == null) accessor.setSessionId("session-1");
        if (accessor.getCommand() == StompCommand.SUBSCRIBE && accessor.getSubscriptionId() == null) {
            accessor.setSubscriptionId("subscription-1");
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void preSend_subscribe_callsIsParticipant() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/topic/conversations/50");
        accessor.setUser(auth(3L, "ROLE_STUDENT"));

        Message<byte[]> message = message(accessor);

        when(messageService.isParticipant(3L, 50L)).thenReturn(true);

        Message<?> result = interceptor.preSend(message, null);
        assertThat(result).isNotNull();
        verify(messageService).isParticipant(3L, 50L);
    }

    @Test
    void preSend_presenceSubscribe_reusesConversationMembershipAuthorization() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/topic/conversations/50/presence");
        accessor.setUser(auth(3L, "ROLE_STUDENT"));
        when(messageService.isParticipant(3L, 50L)).thenReturn(true);

        assertThat(interceptor.preSend(message(accessor), null)).isNotNull();
        verify(messageService).isParticipant(3L, 50L);
    }

    @Test
    void preSend_nonParticipantPresenceSubscribe_isRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/topic/conversations/50/presence");
        accessor.setUser(auth(3L, "ROLE_STUDENT"));
        when(messageService.isParticipant(3L, 50L)).thenReturn(false);

        assertThat(catchThrowableOfType(MessagingException.class,
                () -> interceptor.preSend(message(accessor), null))).isNotNull();
    }

    @Test
    void preSend_subscribeNonParticipant_throwsMessagingException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/topic/conversations/50");
        accessor.setUser(auth(3L, "ROLE_STUDENT"));

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(messageService.isParticipant(3L, 50L)).thenReturn(false);

        MessagingException ex = catchThrowableOfType(MessagingException.class,
                () -> interceptor.preSend(message, null));
        assertThat(ex).isNotNull();
        assertThat(ex.getMessage()).contains("Bu konuşmaya erişiminiz yok");
    }

    @Test
    void preSend_adminCanSubscribeToExistingConversationAsReadOnlyObserver() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/topic/conversations/50");
        accessor.setUser(auth(9L, "ROLE_ADMIN"));
        when(adminConversationService.canObserve(50L)).thenReturn(true);

        assertThat(interceptor.preSend(message(accessor), null)).isNotNull();
        verify(adminConversationService).canObserve(50L);
        verify(messageService, never()).isParticipant(any(), any());
    }

    @Test
    void preSend_adminCannotSubscribeToMissingConversation() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/topic/conversations/404");
        accessor.setUser(auth(9L, "ROLE_ADMIN"));
        when(adminConversationService.canObserve(404L)).thenReturn(false);

        assertThat(catchThrowableOfType(MessagingException.class,
                () -> interceptor.preSend(message(accessor), null))).isNotNull();
    }

    @Test
    void preSend_adminStompSendIsRejectedBeforeController() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/app/conversations/50/send");
        accessor.setUser(auth(9L, "ROLE_ADMIN"));

        MessagingException error = catchThrowableOfType(MessagingException.class,
                () -> interceptor.preSend(message(accessor), null));
        assertThat(error.getMessage()).contains("mesaj gönderemez");
        verifyNoInteractions(messageService, adminConversationService);
    }

    @Test
    void preSend_participantMaySendOnlyToApplicationConversationDestination() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/app/conversations/50/send");
        accessor.setUser(auth(3L, "ROLE_STUDENT"));
        when(messageService.isParticipant(3L, 50L)).thenReturn(true);

        assertThat(interceptor.preSend(message(accessor), null)).isNotNull();
        verify(messageService).isParticipant(3L, 50L);
    }

    @Test
    void preSend_nonParticipantCannotSendToApplicationConversationDestination() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/app/conversations/50/send");
        accessor.setUser(auth(3L, "ROLE_STUDENT"));
        when(messageService.isParticipant(3L, 50L)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void preSend_directTopicPublicationIsRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/topic/conversations/50");
        accessor.setUser(auth(3L, "ROLE_STUDENT"));

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                .isInstanceOf(MessagingException.class);
        verifyNoInteractions(messageService);
    }

    @Test
    void preSend_directQueuePublicationIsRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/queue/notifications-user9");
        accessor.setUser(auth(3L, "ROLE_STUDENT"));

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                .isInstanceOf(MessagingException.class);
        verifyNoInteractions(messageService);
    }

    @Test
    void preSend_notificationSubscriptionIsAllowed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/user/queue/notifications");
        accessor.setUser(auth(3L, "ROLE_STUDENT"));

        assertThat(interceptor.preSend(message(accessor), null)).isNotNull();
        verifyNoInteractions(messageService, adminConversationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/random", "/user/random", "/queue/random"})
    void preSend_unknownSubscriptionDestinationsAreDenied(String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination(destination);
        accessor.setUser(auth(3L, "ROLE_STUDENT"));

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void preSend_expiredSessionRejectsSendAndSubscribeAndClosesTransport() {
        StompSessionAuthentication expired = authentication(3L, Role.STUDENT, 0,
                Instant.now().minusSeconds(60));
        activeUser(3L, Role.STUDENT, 0);

        StompHeaderAccessor send = frame(StompCommand.SEND, "/app/conversations/50/send", expired);
        StompHeaderAccessor subscribe = frame(StompCommand.SUBSCRIBE, "/topic/conversations/50", expired);

        assertThatThrownBy(() -> interceptor.preSend(message(send), null)).isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> interceptor.preSend(message(subscribe), null)).isInstanceOf(MessagingException.class);
        verify(sessionRegistry, times(2)).closeSession("session-1", "Oturum artık geçerli değil");
    }

    @Test
    void preSend_passwordVersionChangeRejectsEstablishedSession() {
        StompHeaderAccessor send = frame(StompCommand.SEND, "/app/conversations/50/send",
                authentication(3L, Role.STUDENT, 0, Instant.now().plusSeconds(60)));
        activeUser(3L, Role.STUDENT, 1);

        assertThatThrownBy(() -> interceptor.preSend(message(send), null)).isInstanceOf(MessagingException.class);
        verify(sessionRegistry).closeSession(eq("session-1"), anyString());
    }

    @Test
    void preSend_passwordChangeTimestampRejectsEstablishedSession() {
        StompHeaderAccessor send = frame(StompCommand.SEND, "/app/conversations/50/send",
                authentication(3L, Role.STUDENT, 0, Instant.now().plusSeconds(60)));
        User user = activeUser(3L, Role.STUDENT, 0);
        user.setPasswordChangedAt(Instant.now());

        assertThatThrownBy(() -> interceptor.preSend(message(send), null)).isInstanceOf(MessagingException.class);
        verify(sessionRegistry).closeSession(eq("session-1"), anyString());
    }

    @Test
    void preSend_roleChangeRejectsEstablishedSession() {
        StompHeaderAccessor send = frame(StompCommand.SEND, "/app/conversations/50/send",
                authentication(3L, Role.STUDENT, 0, Instant.now().plusSeconds(60)));
        activeUser(3L, Role.COACH, 0);

        assertThatThrownBy(() -> interceptor.preSend(message(send), null)).isInstanceOf(MessagingException.class);
        verify(sessionRegistry).closeSession(eq("session-1"), anyString());
    }

    @Test
    void preSend_suspensionRejectsEstablishedSession() {
        StompHeaderAccessor send = frame(StompCommand.SEND, "/app/conversations/50/send",
                authentication(3L, Role.STUDENT, 0, Instant.now().plusSeconds(60)));
        User user = activeUser(3L, Role.STUDENT, 0);
        user.setStatus(UserStatus.SUSPENDED);

        assertThatThrownBy(() -> interceptor.preSend(message(send), null)).isInstanceOf(MessagingException.class);
        verify(sessionRegistry).closeSession(eq("session-1"), anyString());
    }

    @Test
    void preSend_connectionLimitRejectsAndClosesNewSession() {
        StompHeaderAccessor accessor = connect("active-token");
        Jws<Claims> jws = mock(Jws.class);
        when(jws.getPayload()).thenReturn(validClaims(3L, 0));
        when(jwtService.parse("active-token")).thenReturn(jws);
        activeUser(3L, Role.STUDENT, 0);
        when(sessionRegistry.bindAuthenticatedSession("session-1", 3L)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), null)).isInstanceOf(MessagingException.class);
        verify(sessionRegistry).closeSession("session-1", "Connection limit exceeded");
    }

    @Test
    void preSend_subscriptionLimitRejectsAdditionalSubscription() {
        StompHeaderAccessor accessor = frame(StompCommand.SUBSCRIBE, "/user/queue/notifications",
                authentication(3L, Role.STUDENT, 0, Instant.now().plusSeconds(60)));
        activeUser(3L, Role.STUDENT, 0);
        when(sessionRegistry.addSubscription("session-1", "subscription-1")).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("abonelik sınırı");
    }

    private StompHeaderAccessor frame(StompCommand command, String destination, Authentication authentication) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        accessor.setSessionId("session-1");
        accessor.setDestination(destination);
        accessor.setUser(authentication);
        return accessor;
    }

    private Authentication auth(Long userId, String authority) {
        Role role = Role.valueOf(authority.substring("ROLE_".length()));
        activeUser(userId, role, 0);
        return authentication(userId, role, 0, Instant.now().plusSeconds(300));
    }

    private StompSessionAuthentication authentication(Long userId, Role role, int passwordVersion,
                                                       Instant expiresAt) {
        return new StompSessionAuthentication(userId, Instant.now().minusSeconds(30), expiresAt,
                passwordVersion, role);
    }

    private User activeUser(Long userId, Role role, int passwordVersion) {
        User user = new User();
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(role);
        user.setPasswordVersion(passwordVersion);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        return user;
    }

    private Claims validClaims(Long userId, int passwordVersion) {
        return new DefaultClaims(Map.of(
                "sub", userId.toString(),
                "role", "STUDENT",
                "passwordVersion", passwordVersion,
                "iat", Date.from(Instant.now().minusSeconds(30)),
                "exp", Date.from(Instant.now().plusSeconds(300))));
    }
}
