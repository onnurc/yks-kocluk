package com.ykskocluk.demo;

import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.Conversation;
import com.ykskocluk.demo.entity.Package;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.entity.University;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.SubscriptionStatus;
import com.ykskocluk.demo.repository.ConversationRepository;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.PackageRepository;
import com.ykskocluk.demo.repository.SubscriptionRepository;
import com.ykskocluk.demo.repository.UniversityRepository;
import com.ykskocluk.demo.config.JwtProperties;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import javax.crypto.SecretKey;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WebSocket/STOMP auth (Phase 5b) — the critical path. CONNECT must carry a valid JWT or be
 * rejected; SUBSCRIBE to a conversation topic is authorized server-side against membership.
 * The send path reuses MessageService (proven in 5a), so the round-trip here also confirms
 * the shared gate is in effect.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class WebSocketAuthTest {

    @LocalServerPort int port;

    @Autowired JwtService jwtService;
    @Autowired JwtProperties jwtProperties;
    @Autowired UserRepository userRepository;
    @Autowired UniversityRepository universityRepository;
    @Autowired CoachProfileRepository coachProfileRepository;
    @Autowired ConversationRepository conversationRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired PackageRepository packageRepository;

    private WebSocketStompClient stompClient;

    private User student;
    private User coachUser;
    private long conversationId;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new StringMessageConverter());
        // Required for receipt tracking (subscribeHeaders.setReceipt in the round-trip test below) —
        // DefaultStompSession throws IllegalArgumentException without a scheduler configured.
        stompClient.setTaskScheduler(new ConcurrentTaskScheduler());

        University uni = new University();
        uni.setName("WS Uni " + System.nanoTime());
        universityRepository.save(uni);

        coachUser = persistUser(Role.COACH);
        CoachProfile coach = new CoachProfile();
        coach.setUser(coachUser);
        coach.setUniversity(uni);
        coach.setHeadline("Koç");
        coach.setStatus(CoachProfileStatus.APPROVED);
        coach.setMaxStudentCapacity(5);
        coach.setActiveStudentCount(0);
        coachProfileRepository.save(coach);

        student = persistUser(Role.STUDENT);

        // The message gate (CLAUDE.md) applies to WS topic authorization too: isParticipant()
        // requires history access, i.e. a Subscription — a bare Conversation row isn't enough.
        Subscription subscription = new Subscription();
        subscription.setStudent(student);
        subscription.setCoachProfile(coach);
        subscription.setPkg(packageRepository.findByActiveTrueOrderByPriceAsc().get(0));
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartAt(Instant.now());
        subscription.setEndAt(Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS));
        subscriptionRepository.save(subscription);

        Conversation conversation = new Conversation();
        conversation.setStudent(student);
        conversation.setCoachProfile(coach);
        conversation.setLastMessageAt(Instant.now());
        conversationId = conversationRepository.save(conversation).getId();
    }

    @AfterEach
    void tearDown() {
        stompClient.stop();
    }

    // --- Checkpoint 1: CONNECT must carry a valid token ---

    @Test
    void connect_missingToken_rejected() {
        assertThatThrownBy(() -> connect(null, new StompSessionHandlerAdapter() {
        })).isInstanceOf(ExecutionException.class);
    }

    @Test
    void connect_invalidToken_rejected() {
        assertThatThrownBy(() -> connect("not-a-real-jwt", new StompSessionHandlerAdapter() {
        })).isInstanceOf(ExecutionException.class);
    }

    @Test
    void connect_expiredToken_rejected() {
        assertThatThrownBy(() -> connect(expiredToken(student), new StompSessionHandlerAdapter() {
        })).isInstanceOf(ExecutionException.class);
    }

    @Test
    void connect_suspendedUser_rejected() {
        User suspendedUser = persistUser(Role.STUDENT);
        suspendedUser.setStatus(com.ykskocluk.demo.enums.UserStatus.SUSPENDED);
        userRepository.save(suspendedUser);
        assertThatThrownBy(() -> connect(jwtService.generateAccessToken(suspendedUser), new StompSessionHandlerAdapter() {
        })).isInstanceOf(ExecutionException.class);
    }

    @Test
    void connect_deletedUser_rejected() {
        User deletedUser = persistUser(Role.STUDENT);
        deletedUser.setStatus(com.ykskocluk.demo.enums.UserStatus.DELETED);
        userRepository.save(deletedUser);
        assertThatThrownBy(() -> connect(jwtService.generateAccessToken(deletedUser), new StompSessionHandlerAdapter() {
        })).isInstanceOf(ExecutionException.class);
    }

    // --- Valid round-trip (also confirms the shared send path/gate) ---

    @Test
    void connect_validToken_subscribe_send_receive() throws Exception {
        StompSession session = connect(jwtService.generateAccessToken(student), new StompSessionHandlerAdapter() {
        });
        BlockingQueue<String> received = new LinkedBlockingQueue<>();

        session.subscribe("/topic/conversations/" + conversationId, frameHandler(received));

        // enableSimpleBroker (in-memory) never echoes a RECEIPT frame — that's a broker-relay
        // feature, not something StompSubProtocolHandler synthesizes on its own — so there's no
        // receipt to wait on here. Give the SUBSCRIBE a beat to register server-side before
        // sending, since subscribe() returns once the frame is written, not once it's processed.
        Thread.sleep(300);

        session.send("/app/conversations/" + conversationId + "/send", "merhaba ws");

        String payload = received.poll(5, TimeUnit.SECONDS);
        assertThat(payload).isNotNull();
        assertThat(payload).contains("merhaba ws");
    }

    // --- Checkpoint 2: non-participant SUBSCRIBE rejected server-side ---

    @Test
    void subscribe_nonParticipant_rejected() throws Exception {
        User stranger = persistUser(Role.STUDENT); // valid token, but not in the conversation
        RecordingSessionHandler handler = new RecordingSessionHandler();
        StompSession session = connect(jwtService.generateAccessToken(stranger), handler);

        session.subscribe("/topic/conversations/" + conversationId, frameHandler(new LinkedBlockingQueue<>()));

        // The interceptor rejects the SUBSCRIBE → server ERROR frame → session error callback.
        assertThat(handler.errorLatch.await(5, TimeUnit.SECONDS)).isTrue();
    }

    // --- helpers ---

    private StompSession connect(String token, StompSessionHandler handler) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }
        return stompClient.connectAsync("ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(), connectHeaders, handler).get(5, TimeUnit.SECONDS);
    }

    private StompFrameHandler frameHandler(BlockingQueue<String> sink) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                sink.add((String) payload);
            }
        };
    }

    /** A correctly-signed but already-expired token — must be rejected at CONNECT. */
    private String expiredToken(User user) {
        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
        Instant past = Instant.now().minusSeconds(7200);
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(past))
                .expiration(Date.from(past.plusSeconds(3600))) // expired an hour ago
                .signWith(key)
                .compact();
    }

    private User persistUser(Role role) {
        return TestUsers.create(userRepository, role);
    }

    /** Records session-level errors (e.g. a rejected SUBSCRIBE) so the test can await them. */
    private static class RecordingSessionHandler extends StompSessionHandlerAdapter {
        final CountDownLatch errorLatch = new CountDownLatch(1);

        @Override
        public void handleException(StompSession session, StompCommand command,
                                    StompHeaders headers, byte[] payload, Throwable exception) {
            errorLatch.countDown();
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            errorLatch.countDown();
        }
    }
}
