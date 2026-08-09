package com.ykskocluk.demo.security;

import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.service.MessageService;
import com.ykskocluk.demo.service.AdminConversationService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.impl.DefaultClaims;
import io.jsonwebtoken.impl.DefaultJws;
import io.jsonwebtoken.impl.DefaultJwsHeader;
import org.junit.jupiter.api.Test;
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

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;

import static org.assertj.core.api.Assertions.assertThat;
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

    StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthChannelInterceptor(jwtService, messageService, userRepository,
                adminConversationService);
    }

    @Test
    void preSend_connectSuspendedUser_throwsMessagingException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.addNativeHeader("Authorization", "Bearer suspended-token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

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
        accessor.addNativeHeader("Authorization", "Bearer active-token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Claims claims = new DefaultClaims(Map.of("sub", "3", "role", "ADMIN"));
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
        accessor.addNativeHeader("Authorization", "Bearer " + token);
        return accessor;
    }

    private Message<byte[]> message(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void preSend_subscribe_callsIsParticipant() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/topic/conversations/50");
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(3L);
        accessor.setUser(auth);

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(messageService.isParticipant(3L, 50L)).thenReturn(true);

        Message<?> result = interceptor.preSend(message, null);
        assertThat(result).isNotNull();
        verify(messageService).isParticipant(3L, 50L);
    }

    @Test
    void preSend_subscribeNonParticipant_throwsMessagingException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/topic/conversations/50");
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(3L);
        accessor.setUser(auth);

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
        accessor.setUser(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                9L, null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
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
        accessor.setUser(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                9L, null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
        when(adminConversationService.canObserve(404L)).thenReturn(false);

        assertThat(catchThrowableOfType(MessagingException.class,
                () -> interceptor.preSend(message(accessor), null))).isNotNull();
    }

    @Test
    void preSend_adminStompSendIsRejectedBeforeController() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/app/conversations/50/send");
        accessor.setUser(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                9L, null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));

        MessagingException error = catchThrowableOfType(MessagingException.class,
                () -> interceptor.preSend(message(accessor), null));
        assertThat(error.getMessage()).contains("mesaj gönderemez");
        verifyNoInteractions(messageService, adminConversationService);
    }
}
