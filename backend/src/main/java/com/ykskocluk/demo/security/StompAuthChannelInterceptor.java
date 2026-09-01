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
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * STOMP authentication & authorization on the client inbound channel.
 *
 * <ul>
 *   <li><b>CONNECT</b>: the JWT must be present in the {@code Authorization} native header
 *       and valid — otherwise the connection is rejected. The authenticated user is bound
 *       to the STOMP session.</li>
 *   <li><b>SUBSCRIBE</b> to {@code /topic/conversations/{id}}: STUDENT/COACH must be a
 *       participant; authenticated ADMIN may observe an existing conversation read-only.
 *       only the exact {@code /user/queue/notifications} destination is user-scoped; every
 *       unmatched destination and every raw {@code /queue/**} subscription is refused.</li>
 *   <li><b>SEND</b>: only the exact application destination is accepted. Broker-owned and
 *       unknown destinations are rejected; ADMIN remains read-only and participants are checked
 *       before the controller/service path.</li>
 * </ul>
 *
 * <p>All successful participant messages fan out on the existing shared conversation topic;
 * no second admin messaging channel exists.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String TOPIC_PREFIX = "/topic/conversations/";
    private static final Pattern SEND_DESTINATION =
            Pattern.compile("^/app/conversations/([1-9]\\d*)/send$");
    private static final String NOTIFICATION_DESTINATION = "/user/queue/notifications";
    // Broker-side user destination. Clients subscribe to "/user/queue/..."; Spring resolves that
    // to "/queue/...-user{sessionId}" internally — see rejectRawUserQueue.
    private static final String QUEUE_PREFIX = "/queue/";

    private final JwtService jwtService;
    private final MessageService messageService;
    private final UserRepository userRepository;
    private final AdminConversationService adminConversationService;
    private final WebSocketSessionRegistry sessionRegistry;

    public StompAuthChannelInterceptor(JwtService jwtService, MessageService messageService,
                                       UserRepository userRepository,
                                       AdminConversationService adminConversationService,
                                       WebSocketSessionRegistry sessionRegistry) {
        this.jwtService = jwtService;
        this.messageService = messageService;
        this.userRepository = userRepository;
        this.adminConversationService = adminConversationService;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> connect(accessor);
            case SUBSCRIBE -> subscribe(accessor);
            case SEND -> authorizeSend(accessor, revalidate(accessor));
            case UNSUBSCRIBE -> removeSubscription(accessor);
            case DISCONNECT -> releaseSession(accessor);
            default -> { /* other frames: nothing extra */ }
        }
        return message;
    }

    /** Validates the CONNECT-frame JWT; throws (rejecting the connection) if missing/invalid/expired. */
    private void connect(StompHeaderAccessor accessor) {
        StompSessionAuthentication authentication = authenticate(accessor);
        String sessionId = requireSessionId(accessor);
        if (!sessionRegistry.bindAuthenticatedSession(sessionId, authentication.userId())) {
            sessionRegistry.closeSession(sessionId, "Connection limit exceeded");
            throw new MessagingException("WebSocket bağlantı sınırı aşıldı");
        }
        accessor.setUser(authentication);
    }

    private StompSessionAuthentication authenticate(StompHeaderAccessor accessor) {
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
            if (claims.getIssuedAt() == null || claims.getExpiration() == null) {
                throw new MessagingException("Belirteç oturum bilgileri eksik");
            }

            return new StompSessionAuthentication(userId, claims.getIssuedAt().toInstant(),
                    claims.getExpiration().toInstant(), tokenPasswordVersion == null ? 0 : tokenPasswordVersion,
                    user.getRole());
        } catch (MessagingException e) {
            throw e;
        } catch (Exception e) {
            // Invalid signature, malformed, or expired token → reject the CONNECT.
            throw new MessagingException("Geçersiz veya süresi dolmuş belirteç");
        }
    }

    /** A user may only subscribe to a conversation topic they participate in. */
    private void subscribe(StompHeaderAccessor accessor) {
        StompSessionAuthentication authentication = revalidate(accessor);
        authorizeSubscribe(accessor, authentication);
        String subscriptionId = accessor.getSubscriptionId();
        if (subscriptionId == null || subscriptionId.isBlank()) {
            rejectAndClose(accessor, "Abonelik kimliği gerekli");
        }
        if (!sessionRegistry.addSubscription(requireSessionId(accessor), subscriptionId)) {
            throw new MessagingException("WebSocket abonelik sınırı aşıldı");
        }
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor, Authentication authentication) {
        String destination = accessor.getDestination();
        if (NOTIFICATION_DESTINATION.equals(destination)) return;
        rejectRawUserQueue(destination);
        Long conversationId = conversationIdFromDestination(accessor.getDestination());
        if (conversationId == null) {
            throw new MessagingException("Bu abonelik hedefine erişilemez");
        }
        Long userId = currentUserId(authentication);
        boolean authorized = authentication != null && (isAdmin(authentication)
                ? adminConversationService.canObserve(conversationId)
                : userId != null && messageService.isParticipant(userId, conversationId));
        if (!authorized) {
            throw new MessagingException("Bu konuşmaya erişiminiz yok");
        }
    }

    private void authorizeSend(StompHeaderAccessor accessor, Authentication authentication) {
        String destination = accessor.getDestination();
        Matcher matcher = destination == null ? null : SEND_DESTINATION.matcher(destination);
        if (matcher == null || !matcher.matches()) {
            throw new MessagingException("Bu hedefe istemci mesajı gönderilemez");
        }
        if (isAdmin(authentication)) {
            throw new MessagingException("ADMIN konuşma gözlemcisi mesaj gönderemez");
        }
        Long userId = currentUserId(authentication);
        Long conversationId = Long.valueOf(matcher.group(1));
        if (userId == null || !messageService.isParticipant(userId, conversationId)) {
            throw new MessagingException("Bu konuşmaya mesaj gönderemezsiniz");
        }
    }

    private StompSessionAuthentication revalidate(StompHeaderAccessor accessor) {
        Authentication current = currentAuthentication(accessor);
        if (!(current instanceof StompSessionAuthentication authentication)) {
            return rejectAndClose(accessor, "Kimlik doğrulaması gerekli");
        }
        User user = userRepository.findById(authentication.userId()).orElse(null);
        Instant now = Instant.now();
        boolean invalid = user == null || user.getStatus() != UserStatus.ACTIVE
                || !authentication.tokenExpiresAt().isAfter(now)
                || authentication.passwordVersion() != user.getPasswordVersion()
                || authentication.role() != user.getRole()
                || (user.getPasswordChangedAt() != null
                && authentication.tokenIssuedAt().isBefore(user.getPasswordChangedAt()));
        if (invalid) return rejectAndClose(accessor, "Oturum artık geçerli değil");
        return authentication;
    }

    private StompSessionAuthentication rejectAndClose(StompHeaderAccessor accessor, String message) {
        String sessionId = accessor.getSessionId();
        if (sessionId != null) sessionRegistry.closeSession(sessionId, message);
        throw new MessagingException(message);
    }

    private void removeSubscription(StompHeaderAccessor accessor) {
        if (accessor.getSessionId() != null && accessor.getSubscriptionId() != null) {
            sessionRegistry.removeSubscription(accessor.getSessionId(), accessor.getSubscriptionId());
        }
    }

    private void releaseSession(StompHeaderAccessor accessor) {
        if (accessor.getSessionId() != null) sessionRegistry.releaseSession(accessor.getSessionId());
    }

    private String requireSessionId(StompHeaderAccessor accessor) {
        if (accessor.getSessionId() == null || accessor.getSessionId().isBlank()) {
            throw new MessagingException("WebSocket oturum kimliği eksik");
        }
        return accessor.getSessionId();
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
        String remainder = destination.substring(TOPIC_PREFIX.length());
        String idPart = remainder.endsWith("/presence")
                ? remainder.substring(0, remainder.length() - "/presence".length())
                : remainder;
        if (idPart.isBlank() || idPart.contains("/")) {
            throw new MessagingException("Geçersiz konuşma kanalı");
        }
        try {
            return Long.valueOf(idPart);
        } catch (NumberFormatException e) {
            throw new MessagingException("Geçersiz konuşma kanalı");
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
