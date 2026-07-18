package com.ykskocluk.demo.security;

import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.service.MessageService;
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

    StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthChannelInterceptor(jwtService, messageService, userRepository);
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
    void preSend_connectActiveUser_setsAuthentication() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.addNativeHeader("Authorization", "Bearer active-token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Claims claims = new DefaultClaims(Map.of("sub", "3", "role", "STUDENT"));
        Jws jws = mock(Jws.class);
        when(jws.getPayload()).thenReturn(claims);
        when(jwtService.parse("active-token")).thenReturn(jws);

        User activeUser = new User();
        activeUser.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(3L)).thenReturn(Optional.of(activeUser));

        Message<?> result = interceptor.preSend(message, null);
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);

        Authentication auth = (Authentication) resultAccessor.getUser();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(3L);
    }
}
