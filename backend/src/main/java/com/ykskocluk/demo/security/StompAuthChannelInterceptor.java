package com.ykskocluk.demo.security;

import com.ykskocluk.demo.service.MessageService;
import com.ykskocluk.demo.service.AdminConversationService;
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
 *   <li><b>SUBSCRIBE</b> to {@code /topic/conversations/{id}}: STUDENT/COACH must be a
 *       participant; authenticated ADMIN may observe an existing conversation read-only.
 *       {@code /user/queue/**} needs no check — Spring resolves it to the caller's own session —
 *       but a raw {@code /queue/**} subscription is refused outright.</li>
 *   <li><b>SEND</b>: ADMIN is rejected at the channel boundary. Participant sends still pass
 *       through {@code MessageService.sendMessage} for membership/subscription enforcement.</li>
 * </ul>
 *
 * <p>All successful participant messages fan out on the existing shared conversation topic;
 * no second admin messaging channel exists.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String TOPIC_PREFIX = "/topic/conversations/";
    private static final String SEND_PREFIX = "/app/conversations/";
    // Broker-side user destination. Clients subscribe to "/user/queue/..."; Spring resolves that
    // to "/queue/...-user{sessionId}" internally — see rejectRawUserQueue.
    private static final String QUEUE_PREFIX = "/queue/";

    private final JwtService jwtService;
    private final MessageService messageService;
    private final UserRepository userRepository;
    private final AdminConversationService adminConversationService;

    public StompAuthChannelInterceptor(JwtService jwtService, MessageService messageService,
                                       UserRepository userRepository,
                                       AdminConversationService adminConversationService) {
        this.jwtService = jwtService;
        this.messageService = messageService;
        this.userRepository = userRepository;
        this.adminConversationService = adminConversationService;
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
            case SEND -> authorizeSend(accessor);
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

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new MessagingException("Kullanıcı artık mevcut değil"));
            if (user.getStatus() == UserStatus.SUSPENDED) {
                throw new MessagingException("Hesabınız askıya alınmıştır");
            } else if (user.getStatus() == UserStatus.DELETED) {
                throw new MessagingException("Hesap artık kullanılamaz");
            }

            if (user.getPasswordChangedAt() != null
                    && (claims.getIssuedAt() == null
                    || claims.getIssuedAt().toInstant().isBefore(user.getPasswordChangedAt()))) {
                throw new MessagingException("Belirteç parola değişikliğinden önce oluşturulmuş");
            }
            Integer tokenPasswordVersion = claims.get("passwordVersion", Integer.class);
            if ((tokenPasswordVersion == null ? 0 : tokenPasswordVersion) != user.getPasswordVersion()) {
                throw new MessagingException("Belirteç artık geçerli değil");
            }

            return new UsernamePasswordAuthenticationToken(
                    userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        } catch (MessagingException e) {
            throw e;
        } catch (Exception e) {
            // Invalid signature, malformed, or expired token → reject the CONNECT.
            throw new MessagingException("Geçersiz veya süresi dolmuş belirteç");
        }
    }

    /** A user may only subscribe to a conversation topic they participate in. */
    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        rejectRawUserQueue(accessor.getDestination());
        Long conversationId = conversationIdFromDestination(accessor.getDestination());
        if (conversationId == null) {
            return; // not a conversation topic — nothing to authorize here
        }
        Authentication authentication = currentAuthentication(accessor);
        Long userId = currentUserId(authentication);
        boolean authorized = authentication != null && (isAdmin(authentication)
                ? adminConversationService.canObserve(conversationId)
                : userId != null && messageService.isParticipant(userId, conversationId));
        if (!authorized) {
            throw new MessagingException("Bu konuşmaya erişiminiz yok");
        }
    }

    private void authorizeSend(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        Authentication authentication = currentAuthentication(accessor);
        if (destination != null && destination.startsWith(SEND_PREFIX)
                && destination.endsWith("/send") && authentication != null && isAdmin(authentication)) {
            throw new MessagingException("ADMIN konuşma gözlemcisi mesaj gönderemez");
        }
    }

    /**
     * Blocks a client from subscribing straight to the broker-side user queue. Enabling
     * {@code /queue} on the simple broker (WebSocketConfig) makes
     * {@code /queue/notifications-user{sessionId}} a real destination; only the {@code /user}
     * prefix resolves it to the caller's own session, so a raw {@code /queue/**} subscription is
     * an attempt to read someone else's channel. Session ids are unguessable, which makes this
     * defence in depth rather than a live hole — but a legitimate client has no reason to
     * subscribe below {@code /user}, so refusing by construction costs nothing.
     */
    private void rejectRawUserQueue(String destination) {
        if (destination != null && destination.startsWith(QUEUE_PREFIX)) {
            throw new MessagingException("Bu kanala doğrudan abone olunamaz");
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

    private Authentication currentAuthentication(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        return user instanceof Authentication auth ? auth : null;
    }

    private Long currentUserId(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof Long userId ? userId : null;
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
