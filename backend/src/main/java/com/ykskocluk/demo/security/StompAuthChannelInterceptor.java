package com.ykskocluk.demo.security;

import com.ykskocluk.demo.service.MessageService;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

/**
 * STOMP authentication & authorization on the client inbound channel.
 *
 * <ul>
 *   <li><b>CONNECT</b>: the JWT must be present in the {@code Authorization} native header
 *       and valid — otherwise the connection is rejected. The authenticated user is bound
 *       to the STOMP session.</li>
 *   <li><b>SUBSCRIBE</b> to {@code /topic/conversations/{id}}: the bound user must be a
 *       participant of that conversation, or the subscription is rejected server-side.</li>
 * </ul>
 *
 * <p>The send path is authorized inside {@code MessageService.sendMessage} (same gate +
 * membership re-check as REST), so both transports enforce the rules identically.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String TOPIC_PREFIX = "/topic/conversations/";

    private final JwtService jwtService;
    private final MessageService messageService;
    private final UserRepository userRepository;

    public StompAuthChannelInterceptor(JwtService jwtService, MessageService messageService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.messageService = messageService;
        this.userRepository = userRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> accessor.setUser(authenticate(accessor));
            case SUBSCRIBE -> authorizeSubscribe(accessor);
            default -> { /* other frames: nothing extra */ }
        }
        return message;
    }

    /** Validates the CONNECT-frame JWT; throws (rejecting the connection) if missing/invalid/expired. */
    private Authentication authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessagingException("Eksik kimlik doğrulama belirteci");
        }
        try {
            Jws<Claims> jws = jwtService.parse(header.substring(7));
            Claims claims = jws.getPayload();
            Long userId = Long.valueOf(claims.getSubject());

            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                if (user.getStatus() == UserStatus.SUSPENDED) {
                    throw new MessagingException("Hesabınız askıya alınmıştır");
                } else if (user.getStatus() == UserStatus.DELETED) {
                    throw new MessagingException("Hesap artık kullanılamaz");
                }
            }

            String role = claims.get("role", String.class);
            return new UsernamePasswordAuthenticationToken(
                    userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        } catch (MessagingException e) {
            throw e;
        } catch (Exception e) {
            // Invalid signature, malformed, or expired token → reject the CONNECT.
            throw new MessagingException("Geçersiz veya süresi dolmuş belirteç");
        }
    }

    /** A user may only subscribe to a conversation topic they participate in. */
    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        Long conversationId = conversationIdFromDestination(accessor.getDestination());
        if (conversationId == null) {
            return; // not a conversation topic — nothing to authorize here
        }
        Long userId = currentUserId(accessor);
        if (userId == null || !messageService.isParticipant(userId, conversationId)) {
            throw new MessagingException("Bu konuşmaya erişiminiz yok");
        }
    }

    private Long conversationIdFromDestination(String destination) {
        if (destination == null || !destination.startsWith(TOPIC_PREFIX)) {
            return null;
        }
        try {
            return Long.valueOf(destination.substring(TOPIC_PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long currentUserId(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user instanceof Authentication auth && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }
}
